// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.logs.statistics

import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.service

/**
 * Component for storing completion finished type factors with date structure using a map.
 */
@Service
@State(
  name = "CompletionFinishedType",
  storages = [(Storage(value = UserStatisticConstants.STORAGE_FILE_NAME, roamingType = RoamingType.DISABLED))],
  reportStatistic = false
)
class CompletionFinishedTypeComponent : UserFactorComponent<CompletionFinishedTypeComponent.State>(State()) {

  class State : BaseState() {
    class DailyData : BaseState() {
      var explicitCancel: Int by property(0)
      var selected: Int by property(0)
      var invalidated: Int by property(0)
      var other: Int by property(0)
    }

    var dailyData: MutableMap<String, DailyData> by map<String, DailyData>()
  }

  override fun getDailyDataMap(): MutableMap<String, *> = state.dailyData

  /**
   * Records that a completion was explicitly canceled.
   * Increments the explicit cancel counter for today.
   */
  fun fireExplicitCancel() {
    val today = getCurrentDate()
    val dailyData = state.dailyData.getOrPut(today) { State.DailyData() }
    dailyData.explicitCancel += 1
    cleanupOldData()
  }

  /**
   * Records that a completion was selected.
   * Increments the selected counter for today.
   */
  fun fireSelected() {
    val today = getCurrentDate()
    val dailyData = state.dailyData.getOrPut(today) { State.DailyData() }
    dailyData.selected += 1
    cleanupOldData()
  }

  /**
   * Records that a completion was invalidated.
   * Increments the invalidated counter for today.
   */
  fun fireInvalidated() {
    val today = getCurrentDate()
    val dailyData = state.dailyData.getOrPut(today) { State.DailyData() }
    dailyData.invalidated += 1
    cleanupOldData()
  }

  /**
   * Records that a completion was finished in a way other than the tracked categories.
   * Increments the other counter for today.
   */
  fun fireOther() {
    val today = getCurrentDate()
    val dailyData = state.dailyData.getOrPut(today) { State.DailyData() }
    dailyData.other += 1
    cleanupOldData()
  }


  /**
   * Gets the count of completions for a specific finish type.
   * @param key The finish type key ("explicitCancel", "selected", "invalidated", or "other")
   * @return The count of completions for the specified finish type
   */
  fun getCountByKey(key: String): Int {
    return state.dailyData.values.sumOf { dailyData ->
      when (key) {
        "explicitCancel" -> dailyData.explicitCancel
        "selected" -> dailyData.selected
        "invalidated" -> dailyData.invalidated
        "other" -> dailyData.other
        else -> 0
      }
    }
  }

  /**
   * Gets the total count of completions across all finish types.
   * @return The total count of completions
   */
  fun getTotalCount(): Int {
    return state.dailyData.values.sumOf { dailyData ->
      dailyData.explicitCancel + dailyData.selected + dailyData.invalidated + dailyData.other
    }
  }

  companion object {
    @JvmStatic
    fun getInstance(): CompletionFinishedTypeComponent = service()
  }
}

/**
 * Calculator class for completion finish ratios.
 * Provides calculated metrics based on the underlying component data.
 */
class CompletionFinishTypeFeatures() {
  private val component = CompletionFinishedTypeComponent.getInstance()

  fun getSelectedRatio(): Double {
    val total = component.getTotalCount()
    return if (total > 0) component.getCountByKey("selected").toDouble() / total else 0.0
  }

  fun getInvalidatedRatio(): Double {
    val total = component.getTotalCount()
    return if (total > 0) component.getCountByKey("invalidated").toDouble() / total else 0.0
  }

  fun getExplicitCancelRatio(): Double {
    val total = component.getTotalCount()
    return if (total > 0) component.getCountByKey("explicitCancel").toDouble() / total else 0.0
  }

  fun hasCompletionStatistics(): Boolean = component.getTotalCount() > 0
}
