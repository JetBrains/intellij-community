// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.logs.statistics

import java.time.Instant
import java.time.LocalDate
import kotlin.math.pow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.DurationUnit

private const val PREV_SELECTED = "prev_selected"
private const val SELECTION = "selection"
private const val SHOW_UP = "show_up"

private const val GLOBAL_ACCEPTANCE_RATE = 0.3
private const val GLOBAL_ALPHA = 10

val DECAY_DURATIONS: List<Duration> = listOf(1.hours, 1.days, 7.days)

fun lastTimeName(name: String): String = "last_${name}_time"
fun decayingCountName(name: String, decayDuration: Duration): String = "${name}_count_decayed_by_$decayDuration"

class AccRateFactorsReader(factor: DailyAggregatedDoubleFactor) : UserFactorReaderBase(factor) {
  fun lastSelectionTimeToday(): Double? = getTodayFactor(lastTimeName(SELECTION))
  fun lastShowUpTimeToday(): Double? = getTodayFactor(lastTimeName(SHOW_UP))
  fun prevSelected(): Double? = getTodayFactor(PREV_SELECTED)

  private fun getTodayFactor(name: String) = factor.onDate(LocalDate.now())?.get(name)

  fun smoothedAcceptanceRate(decayDuration: Duration): Double {
    val timestamp = currentTimestamp()
    return globallySmoothedRatio(selectionCountDecayedBy(decayDuration, timestamp), showUpCountDecayedBy(decayDuration, timestamp))
  }

  fun selectionCountDecayedBy(decayDuration: Duration, timestamp: Long = currentTimestamp()): Double =
    factor.aggregateDecayingCount(SELECTION, decayDuration, timestamp)

  fun showUpCountDecayedBy(decayDuration: Duration, timestamp: Long = currentTimestamp()): Double =
    factor.aggregateDecayingCount(SHOW_UP, decayDuration, timestamp)

}

//private fun currentEpochSeconds() = Instant.now().epochSecond.toDouble()
private fun currentTimestamp() = Instant.now().toEpochMilli()


class AccRateFactorsUpdater(factor: MutableDoubleFactor) : UserFactorUpdaterBase(factor) {
  fun fireLookupElementSelected() {
    val timestamp = currentTimestamp()
    for (duration in DECAY_DURATIONS) {
      factor.increment(SELECTION, duration, timestamp)
      factor.increment(SHOW_UP, duration, timestamp)
    }
    factor.updateOnDate(LocalDate.now()) {
      this[lastTimeName(SELECTION)] = timestamp.toDouble()
      this[lastTimeName(SHOW_UP)] = timestamp.toDouble()
    }
    factor.prevSelected(true)
  }

  fun fireLookupElementShowUp() {
    val timestamp = currentTimestamp()
    for (duration in DECAY_DURATIONS) {
      factor.increment(SHOW_UP, duration, timestamp)
    }
    factor.updateOnDate(LocalDate.now()) {
      this[lastTimeName(SHOW_UP)] = timestamp.toDouble()
    }
    factor.prevSelected(false)
  }
}

fun DailyAggregatedDoubleFactor.aggregateDecayingCount(name: String, decayDuration: Duration, timestamp: Long): Double {
  var result = 0.0
  for (day in availableDays()) {
    result += get(name, decayDuration, day, timestamp) ?: 0.0
  }
  return result
}

fun DailyAggregatedDoubleFactor.get(name: String, decayDuration: Duration, day: LocalDate, timestamp: Long): Double? {
  val onDate = onDate(day) ?: return null
  val lastTimeName = lastTimeName(name)
  val decayingCountName = decayingCountName(name, decayDuration)
  val lastTime = onDate[lastTimeName] ?: return null
  val decayingCount = onDate[decayingCountName]
  return decayingCount.decay(timestamp - lastTime, decayDuration)
}

fun MutableDoubleFactor.increment(name: String, decayDuration: Duration, timestamp: Long) {
  updateOnDate(LocalDate.now()) {
    val lastTimeName = lastTimeName(name)
    val decayingCountName = decayingCountName(name, decayDuration)
    this[decayingCountName] = this[lastTimeName]?.let { this[decayingCountName].decay(timestamp - it, decayDuration) + 1 } ?: 1.0
    //this[lastTimeName] = timestamp.toDouble()
  }
}

private fun MutableDoubleFactor.prevSelected(boolean: Boolean) {
  updateOnDate(LocalDate.now()) {
    this[PREV_SELECTED] = if (boolean) 1.0 else 0.0
  }
}

private fun Double?.decay(duration: Double, decayDuration: Duration) =
  if (this == null) 0.0
  else if (duration * this == 0.0) this
  else 0.5.pow(duration / decayDuration.toDouble(DurationUnit.MILLISECONDS)) * this

private fun globallySmoothedRatio(quotient: Double?, divisor: Double?) =
  if (divisor == null) GLOBAL_ACCEPTANCE_RATE
  else ((quotient ?: 0.0) + GLOBAL_ACCEPTANCE_RATE * GLOBAL_ALPHA) / (divisor + GLOBAL_ALPHA)

//private fun timeSince(epochSeconds: Double) = (Instant.now().epochSecond - epochSeconds.toLong()).toString()
internal fun timeSince(epochSeconds: Double) = Instant.now().toEpochMilli() - epochSeconds.toLong()
