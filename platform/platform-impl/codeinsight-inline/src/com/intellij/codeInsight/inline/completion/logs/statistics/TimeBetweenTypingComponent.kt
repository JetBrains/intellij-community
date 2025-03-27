// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.logs.statistics

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service

/**
 * Component for storing time between typing events with date structure using a map.
 */
@Service
@State(
  name = "TimeBetweenTyping",
  storages = [(Storage(value = UserStatisticConstants.STORAGE_FILE_NAME, roamingType = RoamingType.DISABLED))],
  reportStatistic = false
)
internal class TimeBetweenTypingComponent : UserFactorComponent<TimeBetweenTypingComponent.State>(State()) {

  class State : BaseState() {
    class DailyData : BaseState() {
      var count: Int by property(0)
      var totalTime: Long by property(0L)

      fun increment(time: Long) {
        totalTime += time
        count += 1
      }
    }

    var dailyData: MutableMap<String, DailyData> by map<String, DailyData>()

    fun incrementOnDay(day: String, time: Long) {
      dailyData.getOrPut(day) { DailyData() }.increment(time)
      incrementModificationCount()
    }
  }

  override fun getDailyDataMap(): MutableMap<String, *> = state.dailyData

  /**
   * Records a typing event with the specified delay since the last typing event.
   * @param delayMs The delay in milliseconds since the last typing event
   */
  fun fireTypingPerformed(delayMs: Long) {
    state.incrementOnDay(getCurrentDate(), delayMs)
    cleanupOldData()
  }


  /**
   * Calculates the average time between typing events.
   * @return The average time in milliseconds, or null if no typing events have been recorded
   */
  fun getAverageTimeBetweenTyping(): Double? {
    val totalCount = state.dailyData.values.sumOf { it.count }
    val totalTime = state.dailyData.values.sumOf { it.totalTime }
    return safeDiv(totalTime, totalCount.toLong())
  }

  companion object {
    fun getInstance(): TimeBetweenTypingComponent = service()
  }
}

/**
 * Analyzer class for typing speed metrics.
 * Provides calculated metrics based on the underlying component data.
 */
internal class TimeBetweenTypingFeatures() {
  private val component
    get() = TimeBetweenTypingComponent.getInstance()

  fun getAverageTypingSpeed(): Double = component.getAverageTimeBetweenTyping() ?: 0.0
}
