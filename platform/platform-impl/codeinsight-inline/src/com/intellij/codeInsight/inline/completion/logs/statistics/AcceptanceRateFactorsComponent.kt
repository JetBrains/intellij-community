// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.logs.statistics

import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.service
import com.intellij.util.xmlb.annotations.XMap
import java.time.Instant
import kotlin.math.abs
import kotlin.math.pow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.DurationUnit

/**
 * Component for storing acceptance rate factors.
 */
@Service
@State(
  name = "AcceptanceRateFactors",
  storages = [(Storage(value = UserStatisticConstants.STORAGE_FILE_NAME, roamingType = RoamingType.DISABLED))],
  reportStatistic = false
)
internal class AcceptanceRateFactorsComponent : SimplePersistentStateComponent<AcceptanceRateFactorsComponent.State>(State()) {

  class State : BaseState() {
    var lastSelectionTime: Long by property(0L)
    var lastShowUpTime: Long by property(0L)
    var prevSelected: Boolean by property(false)
    @get:XMap
    val selectionCounts: MutableMap<Long, Double> by map()
    @get:XMap
    val showUpCounts: MutableMap<Long, Double> by map()

    init {
      DECAY_DURATIONS.forEach { duration ->
        selectionCounts.put(duration.inWholeHours, 0.0)
        showUpCounts.put(duration.inWholeHours, 0.0)
      }
    }
  }

  /**
   * Records that an inline completion element was selected by the user.
   * Updates both selection and show-up counts with decay based on time since last event.
   */
  fun fireElementSelected() {
    val timestamp = currentTimestamp()
    with(state) {
      for (duration in DECAY_DURATIONS) {
        selectionCounts[duration.inWholeHours] = decay(
          selectionCounts[duration.inWholeHours] ?: 0.0,
          timestamp - lastSelectionTime,
          duration
        ) + 1.0
        showUpCounts[duration.inWholeHours] = decay(
          showUpCounts[duration.inWholeHours] ?: 0.0,
          timestamp - lastShowUpTime,
          duration
        ) + 1.0
      }

      lastSelectionTime = timestamp
      lastShowUpTime = timestamp
      prevSelected = true
    }
  }

  /**
   * Records that an inline completion element was shown to the user.
   * Updates show-up counts with decay based on time since last show-up event.
   */
  fun fireElementShowUp() {
    val timestamp = currentTimestamp()
    with(state) {
      for (duration in DECAY_DURATIONS) {
        showUpCounts[duration.inWholeHours] = decay(showUpCounts[duration.inWholeHours] ?: 0.0, timestamp - lastShowUpTime, duration) + 1.0
      }

      lastShowUpTime = timestamp
      prevSelected = false
    }
  }

  /**
   * Returns the timestamp of the last selection event.
   * @return Timestamp in milliseconds since epoch
   */
  val lastSelectionTimeToday: Long
    get() = state.lastSelectionTime

  /**
   * Returns the timestamp of the last show-up event.
   * @return Timestamp in milliseconds since epoch
   */
  val lastShowUpTimeToday: Long
    get() = state.lastShowUpTime

  /**
   * Returns whether the last shown completion was selected.
   * @return True if the last completion was selected, false otherwise
   */
  val prevSelected: Boolean
    get() = state.prevSelected

  /**
   * Calculates the decayed selection count for a specific decay duration.
   * @param decayDuration The duration to use for decay calculation
   * @param timestamp The current timestamp (defaults to now)
   * @return The decayed selection count
   */
  fun selectionCountDecayedBy(decayDuration: Duration, timestamp: Long = currentTimestamp()): Double {
    return with(state) {
      if (decayDuration in DECAY_DURATIONS) {
        decay(selectionCounts[decayDuration.inWholeHours] ?: 0.0, timestamp - lastSelectionTime, decayDuration)
      }
      else {
        0.0
      }
    }
  }

  /**
   * Calculates the decayed show-up count for a specific decay duration.
   * @param decayDuration The duration to use for decay calculation
   * @param timestamp The current timestamp (defaults to now)
   * @return The decayed show-up count
   */
  fun showUpCountDecayedBy(decayDuration: Duration, timestamp: Long = currentTimestamp()): Double {
    return with(state) {
      if (decayDuration in DECAY_DURATIONS) {
        decay(showUpCounts[decayDuration.inWholeHours] ?: 0.0, timestamp - lastShowUpTime, decayDuration)
      }
      else {
        0.0
      }
    }
  }

  /**
   * Calculates the smoothed acceptance rate for a specific decay duration.
   * Uses a global smoothing factor to handle cases with limited data.
   * @param decayDuration The duration to use for decay calculation
   * @return The smoothed acceptance rate between 0.0 and 1.0
   */
  fun smoothedAcceptanceRate(decayDuration: Duration): Double {
    val timestamp = currentTimestamp()
    return globallySmoothedRatio(
      selectionCountDecayedBy(decayDuration, timestamp),
      showUpCountDecayedBy(decayDuration, timestamp)
    )
  }

  companion object {
    val DECAY_DURATIONS: List<Duration> = listOf(1.hours, 1.days, 7.days)

    fun getInstance(): AcceptanceRateFactorsComponent = service()

    private fun currentTimestamp() = Instant.now().toEpochMilli()

    /**
     * Computes a decayed value based on the elapsed duration and a given decay period.
     * The smaller the decayDuration, the faster past events are neglected.
     * Heuristically, it calculates the "average" for the last `decayDuration` time period.
     *
     *
     * @param value The current value before decay.
     * @param duration The elapsed time since the last event.
     * @param decayDuration The duration used to calculate the decay factor.
     * @return The updated (decayed) value.
     */
    private fun decay(value: Double, duration: Long, decayDuration: Duration): Double =
      //`==` for Double may work incorrectly, so `<` should be used here
      if (duration == 0L || abs(value) < 1e-9) value
      else 0.5.pow(duration.toDouble() / decayDuration.toDouble(DurationUnit.MILLISECONDS)) * value

    /**
     * Computes a globally smoothed ratio from quotient and divisor values.
     * Actually, it adds a `GLOBAL_ALPHA` number of events with a value of `GLOBAL_ACCEPTANCE_RATE`.
     * For example, if ALPHA = 10 and AR = 0.3, a person with 2 show-ups and 1 selection would have
     * smoothedAR = (1 + 10*0.3) / (2 + 10) = 0.33 instead of 0.5.
     * This smoothing is applied to produce more realistic statistics for new users.
    */
    private fun globallySmoothedRatio(quotient: Double?, divisor: Double?): Double {
      val smoothedQuotient = (quotient ?: 0.0) + UserStatisticConstants.GLOBAL_ACCEPTANCE_RATE * UserStatisticConstants.GLOBAL_ALPHA
      val smoothedDivisor = (divisor ?: 0.0) + UserStatisticConstants.GLOBAL_ALPHA
      return smoothedQuotient / smoothedDivisor
    }
  }
}

/**
 * Calculator class for acceptance rate metrics.
 * Provides calculated metrics based on the underlying component data.
 */
internal class AcceptanceRateFeatures() {
  private val component
    get() = AcceptanceRateFactorsComponent.getInstance()

  val timeSinceLastSelection: Long
    get() = Instant.now().toEpochMilli() - component.lastSelectionTimeToday

  val timeSinceLastShowup: Long
    get() = Instant.now().toEpochMilli() - component.lastShowUpTimeToday

  val prevSelected: Boolean
    get() = component.prevSelected

  fun smoothedAcceptanceRate(decayDuration: Duration): Double =
    component.smoothedAcceptanceRate(decayDuration)

  fun selectionCountDecayedBy(decayDuration: Duration): Double =
    component.selectionCountDecayedBy(decayDuration, currentTimestamp())

  fun showUpCountDecayedBy(decayDuration: Duration): Double =
    component.showUpCountDecayedBy(decayDuration, currentTimestamp())

  companion object {
    private fun currentTimestamp() = Instant.now().toEpochMilli()
  }
}
