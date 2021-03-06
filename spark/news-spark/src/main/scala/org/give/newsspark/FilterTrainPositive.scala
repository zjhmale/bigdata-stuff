package org.give.newsspark

import org.apache.spark.{SparkConf, SparkContext}

/**
 * Created by zjh on 14-10-5.
 * 因为原始数据中有坑，存在某一个用户在同一个时间点上点击多个新闻的情况，所以在之前生成的训练正例中总共有10078个训练样本
 * 这个spark-app是用来将相同用户id和相同浏览时间的几条记录中取最后一条，从而保证10000条和用户id数目一样的训练正例
 */
object FilterTrainPositive {
    def main(args: Array[String]) {
        val sc = new SparkContext(new SparkConf().setAppName("Train Positive"))
        val trainpositive = sc.textFile("/user/root/trainpositive/part-00000")
        println(trainpositive.count)

        val duplicatedPair = trainpositive.map(line => line.split("\t")).map(line => (line(0), line(2))).groupBy(identity).collect { case (x,ys) if ys.size > 1 => x }
        val duplicatedPairCollect = duplicatedPair.collect

        //http://stackoverflow.com/questions/23793117/nullpointerexception-in-scala-spark-appears-to-be-caused-be-collection-type
        //it seems that spark does not support nested of RDD operation, so should use the collect action outside RDD transform
        val duplicatedRecords = trainpositive.map{ line =>
            line.split("\t")
        }.filter{ line =>
            duplicatedPairCollect.contains((line(0), line(2)))
        }

        //对于某一个用户id在同一个时间内点击多个新闻的情况，则选择最后一条点击记录放在训练正例中
        val filterDuplicatedRecords = duplicatedRecords.groupBy{ record =>
            record(0)
        }.map{ record =>
            //record._1
            val innerArray = record._2.toArray
            innerArray(innerArray.size-1)
        }

        val unduplicatedRecords = trainpositive.map{ line =>
            line.split("\t")
        }.filter{ line =>
            !duplicatedPairCollect.contains((line(0), line(2)))
        }

        //union rdds https://groups.google.com/forum/#!topic/spark-users/wB3lLLrid2g
        //or could use filterDuplicatedRecords.union(unduplicatedRecords)
        val finalTrainPositive = sc.union(filterDuplicatedRecords, unduplicatedRecords)
        finalTrainPositive.map{ record =>
            record.mkString("\t")
        }.cache().coalesce(1).saveAsTextFile("/user/root/finaltrainpositive")
        System.out.println("generate final train positive successful")

        //validate
        //sc.textFile("/user/root/finaltrainpositive/part-00000").map(line => line.split("\t")).map(line => (line(0), line(2))).groupBy(identity).collect { case (x,ys) if ys.size > 1 => x }.collect
        //=> Array[(String, String)] = Array() it's done there is no duplicate record with userid and visit_timestamp in trainpositive data
    }
}
