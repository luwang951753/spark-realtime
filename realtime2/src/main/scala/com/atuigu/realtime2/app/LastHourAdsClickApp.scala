package com.atuigu.realtime2.app

import com.atguigu.realtime.bean.AdsInfo
import com.atguigu.realtime.util.RedisUtil
import org.apache.spark.streaming.dstream.DStream
import org.apache.spark.streaming.{Minutes, Seconds}
import org.json4s.jackson.JsonMethods
import redis.clients.jedis.Jedis

/**
  * Author lzc
  * Date 2019-09-27 15:31
  */
object LastHourAdsClickApp {
    def statLastHourClick(adsInfoDStream: DStream[AdsInfo]) = {
        val adsInfoDStreamWithWindow: DStream[AdsInfo] = adsInfoDStream.window(Minutes(60), Seconds(5))
        
        // (adsId, List((10:21, 1000)))
        val adsAndHMCountDSteam: DStream[(String, List[(String, Int)])] = adsInfoDStreamWithWindow.map(adsInfo => ((adsInfo.adsId, adsInfo.hmString), 1)) // ((广告, 10:20), 1)
            .reduceByKey(_ + _)
            .map {
                case ((ads, hm), count) => (ads, (hm, count))
            }
            .groupByKey
            .map {
                case (ads, it) => (ads, it.toList.sortBy(_._1))  // 按照 hh:mm 升序排列
            }
        
        // 写入到redis
        adsAndHMCountDSteam.foreachRDD(rdd => {
            rdd.foreachPartition(it => {
                val client: Jedis = RedisUtil.getJedisClient
                
                it.foreach {
                    case (ads, hmCountList) =>
                        import org.json4s.JsonDSL._
                        val v = JsonMethods.compact(JsonMethods.render(hmCountList))
                        client.hset("last_hour_click", ads, v)
                }
                
                client.close()
            })
        })
    }
}
