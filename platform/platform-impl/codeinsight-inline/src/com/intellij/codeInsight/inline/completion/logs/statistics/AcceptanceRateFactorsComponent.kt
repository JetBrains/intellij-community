// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.logs.statistics

import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.service
import java.time.Instant
import kotlin.math.pow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.DurationUnit

/**
 * Component for storing acceptance rate factors.
 */
val DECAY_DURATIONS: List<Duration> = listOf(1.hours, 1.days, 7.days)

@Service
@State(
  name = "AcceptanceRateFactors",
  storages = [(Storage(value = UserStatisticConstants.STORAGE_FILE_NAME, roamingType = RoamingType.DISABLED))],
  reportStatistic = false
)
class AcceptanceRateFactorsComponent : SimplePersistentStateComponent<AcceptanceRateFactorsComponent.State>(State()) {

  class State : BaseState() {
    var lastSelectionTime: Long by property(0L)
    var lastShowUpTime: Long by property(0L)
    var prevSelected: Boolean by property(false)
    var selectionCounts: MutableMap<String, Double> by map()
    var showUpCounts: MutableMap<String, Double> by map()

    init {
      DECAY_DURATIONS.forEach { duration ->
        selectionCounts[duration.toString()] = 0.0
        showUpCounts[duration.toString()] = 0.0
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
        selectionCounts[duration.toString()] = decay(selectionCounts[duration.toString()]
                                                     ?: 0.0, timestamp - lastSelectionTime, duration) + 1.0
        showUpCounts[duration.toString()] = decay(showUpCounts[duration.toString()] ?: 0.0, timestamp - lastShowUpTime, duration) + 1.0
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
        showUpCounts[duration.toString()] = decay(showUpCounts[duration.toString()] ?: 0.0, timestamp - lastShowUpTime, duration) + 1.0
      }

      lastShowUpTime = timestamp
      prevSelected = false
    }
  }

  /**
   * Returns the timestamp of the last selection event.
   * @return Timestamp in milliseconds since epoch
   */
  fun lastSelectionTimeToday(): Long = state.lastSelectionTime

  /**
   * Returns the timestamp of the last show-up event.
   * @return Timestamp in milliseconds since epoch
   */
  fun lastShowUpTimeToday(): Long = state.lastShowUpTime

  /**
   * Returns whether the last shown completion was selected.
   * @return True if the last completion was selected, false otherwise
   */
  fun prevSelected(): Boolean = state.prevSelected

  /**
   * Calculates the decayed selection count for a specific decay duration.
   * @param decayDuration The duration to use for decay calculation
   * @param timestamp The current timestamp (defaults to now)
   * @return The decayed selection count
   */
  fun selectionCountDecayedBy(decayDuration: Duration, timestamp: Long = currentTimestamp()): Double {
    return with(state) {
      if (decayDuration in DECAY_DURATIONS) {
        decay(selectionCounts[decayDuration.toString()] ?: 0.0, timestamp - lastSelectionTime, decayDuration)
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
        decay(showUpCounts[decayDuration.toString()] ?: 0.0, timestamp - lastShowUpTime, decayDuration)
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
    @JvmStatic
    fun getInstance(): AcceptanceRateFactorsComponent = service()

    private fun currentTimestamp() = Instant.now().toEpochMilli()

    private fun decay(value: Double, duration: Long, decayDuration: Duration): Double =
      if (duration * value == 0.0) value
      else 0.5.pow(duration.toDouble() / decayDuration.toDouble(DurationUnit.MILLISECONDS)) * value

    private fun globallySmoothedRatio(quotient: Double?, divisor: Double?): Double =
      if (divisor == null) UserStatisticConstants.GLOBAL_ACCEPTANCE_RATE
      else ((quotient
             ?: 0.0) + UserStatisticConstants.GLOBAL_ACCEPTANCE_RATE * UserStatisticConstants.GLOBAL_ALPHA) / (divisor + UserStatisticConstants.GLOBAL_ALPHA)
  }
}

/**
 * Calculator class for acceptance rate metrics.
 * Provides calculated metrics based on the underlying component data.
 */
class AcceptanceRateFeatures() {
  private val component = AcceptanceRateFactorsComponent.getInstance()

  fun getTimeSinceLastSelection(): Long =
    Instant.now().toEpochMilli() - component.lastSelectionTimeToday()

  fun getTimeSinceLastShowup(): Long =
    Instant.now().toEpochMilli() - component.lastShowUpTimeToday()

  fun prevSelected(): Boolean = component.prevSelected()

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
