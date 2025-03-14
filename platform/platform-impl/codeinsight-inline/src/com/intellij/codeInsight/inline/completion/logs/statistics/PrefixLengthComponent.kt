// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.logs.statistics

import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.service

/**
 * Component for storing prefix length factors with date structure using a map.
 */
@Service
@State(
  name = "PrefixLength",
  storages = [(Storage(value = UserStatisticConstants.STORAGE_FILE_NAME, roamingType = RoamingType.DISABLED))],
  reportStatistic = false
)
class PrefixLengthComponent : UserFactorComponent<PrefixLengthComponent.State>(State()) {

  class State : BaseState() {
    class DailyData : BaseState() {
      var prefixLengths: MutableMap<Int, Int> by map()
    }

    var dailyData: MutableMap<String, DailyData> by map<String, DailyData>()
  }

  override fun getDailyDataMap(): MutableMap<String, *> = state.dailyData

  /**
   * Records that a completion was performed with a specific prefix length.
   * @param prefixLength The length of the prefix that triggered the completion
   */
  fun fireCompletionPerformed(prefixLength: Int) {
    val today = getCurrentDate()
    val dailyData = state.dailyData.getOrPut(today) { State.DailyData() }
    dailyData.prefixLengths[prefixLength] = (dailyData.prefixLengths[prefixLength] ?: 0) + 1

    cleanupOldData()
  }


  /**
   * Gets a map of prefix lengths to their occurrence counts.
   * @return Map where keys are prefix lengths and values are occurrence counts
   */
  fun getCountsByPrefixLength(): Map<Int, Int> {
    val result = mutableMapOf<Int, Int>()
    for (dailyData in state.dailyData.values) {
      for ((length, count) in dailyData.prefixLengths.entries) {
        result[length] = (result[length] ?: 0) + count
      }
    }
    return result
  }

  /**
   * Calculates the average prefix length across all recorded completions.
   * @return The average prefix length, or null if no completions have been recorded
   */
  fun getAveragePrefixLength(): Double? {
    val lengthToCount = getCountsByPrefixLength()
    if (lengthToCount.isEmpty()) return null

    val totalChars = lengthToCount.asSequence().sumOf { it.key * it.value }
    val completionCount = lengthToCount.asSequence().sumOf { it.value }

    if (completionCount == 0) return null
    return totalChars.toDouble() / completionCount
  }

  companion object {
    @JvmStatic
    fun getInstance(): PrefixLengthComponent = service()
  }
}

/**
 * Analyzer class for prefix length metrics.
 * Provides calculated metrics based on the underlying component data.
 */
class PrefixLengthFeatures() {
  private val component = PrefixLengthComponent.getInstance()

  fun getMostFrequentPrefixLength(): Int {
    return component.getCountsByPrefixLength()
             .maxByOrNull { it.value }?.key ?: 0
  }

  fun getAveragePrefixLength(): Double = component.getAveragePrefixLength() ?: 0.0
}
