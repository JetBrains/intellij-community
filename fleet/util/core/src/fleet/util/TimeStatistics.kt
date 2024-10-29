// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util

import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.measureTime

class TimeStatCounter(val capacity: Int = 5000) {
  val measurements: ArrayList<Duration> = ArrayList(capacity)
  var lastDuration: Duration = 0.nanoseconds
  
  data class Stats(val median: Duration,
                   val mean: Duration,
                   val perc95: Duration,
                   val stdDev: Duration)
  
  private fun median(): Duration {
    val median = measurements.size / 2
    return if (measurements.size % 2 == 0) {
      (measurements[median - 1] + measurements[median]) / 2
    } else {
      measurements[median]
    }
  }
  
  private fun perc95(): Duration {
    val index = (0.95 * measurements.size).toInt()
    return if (measurements.size > index) measurements[index] else measurements.last()
  }
  
  private fun callcStats(): Stats {
    measurements.sort()
    val median = median()
    val perc95 = perc95()
    val mean = (measurements.map { it.inWholeNanoseconds }.sum().toDouble() / measurements.size).nanoseconds
    val sum: Double = measurements.sumOf {
      (it - mean).inWholeNanoseconds.toDouble().pow(2)
    }
    val stdDev = sqrt(sum / measurements.size).toLong().nanoseconds
    return Stats(median = median,
                 mean = mean,
                 perc95 = perc95,
                 stdDev = stdDev)
  }
  
  inline fun <T> sumTime(f: () -> T): T {
    var result: T
    lastDuration += measureTime {
      result = f()
    }
    return result
  }

  fun commit(): Stats? {
    measurements.add(lastDuration)
    lastDuration = 0.nanoseconds
    return if (measurements.size == capacity) {
      val stats = callcStats()
      measurements.clear()
      stats
    } else {
      null
    }
  }
}