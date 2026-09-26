// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.logs.statistics

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.util.xmlb.annotations.XMap

/**
 * Enum representing the different types of completion finishes.
 */
internal enum class CompletionFinishType {
  EXPLICIT_CANCEL,
  SELECTED,
  INVALIDATED,
  OTHER
}

/**
 * Component for storing completion finished type factors with date structure using a map.
 */
@Service
@State(
  name = "CompletionFinishedType",
  storages = [(Storage(value = UserStatisticConstants.STORAGE_FILE_NAME, roamingType = RoamingType.DISABLED))],
  reportStatistic = false
)
internal class CompletionFinishedTypeComponent : SimplePersistentStateComponent<CompletionFinishedTypeComponent.State>(State()) {

  class State : UserFactorState<State.DailyData, CompletionFinishType>({ DailyData() }) {
    class DailyData : BaseState(), ModifiableStorage<CompletionFinishType> {
      var explicitCancel: Int by property(0)
      var selected: Int by property(0)
      var invalidated: Int by property(0)
      var other: Int by property(0)

      override fun update(param: CompletionFinishType) {
        when (param) {
          CompletionFinishType.EXPLICIT_CANCEL -> explicitCancel += 1
          CompletionFinishType.SELECTED -> selected += 1
          CompletionFinishType.INVALIDATED -> invalidated += 1
          CompletionFinishType.OTHER -> other += 1
        }
      }
    }

    @get:XMap
    override var dailyDataMap: MutableMap<String, DailyData> by map()
  }

  /**
   * Records that a completion was explicitly canceled.
   * Increments the explicit cancel counter for today.
   */
  fun fireExplicitCancel() {
    state.increment(CompletionFinishType.EXPLICIT_CANCEL)
  }

  /**
   * Records that a completion was selected.
   * Increments the selected counter for today.
   */
  fun fireSelected() {
    state.increment(CompletionFinishType.SELECTED)
  }

  /**
   * Records that a completion was invalidated.
   * Increments the invalidated counter for today.
   */
  fun fireInvalidated() {
    state.increment(CompletionFinishType.INVALIDATED)
  }

  /**
   * Records that a completion was finished in a way other than the tracked categories.
   * Increments the other counter for today.
   */
  fun fireOther() {
    state.increment(CompletionFinishType.OTHER)
  }


  /**
   * Gets the count of completions for a specific finish type.
   * @param type The completion finish type
   * @return The count of completions for the specified finish type
   */
  fun getCountByType(type: CompletionFinishType): Int {
    return state.dailyDataMap.values.sumOf { dailyData ->
      when (type) {
        CompletionFinishType.EXPLICIT_CANCEL -> dailyData.explicitCancel
        CompletionFinishType.SELECTED -> dailyData.selected
        CompletionFinishType.INVALIDATED -> dailyData.invalidated
        CompletionFinishType.OTHER -> dailyData.other
      }
    }
  }

  /**
   * Gets the total count of completions across all finish types.
   * @return The total count of completions
   */
  fun getTotalCount(): Int {
    return state.dailyDataMap.values.sumOf { dailyData ->
      dailyData.explicitCancel + dailyData.selected + dailyData.invalidated + dailyData.other
    }
  }

  companion object {
    fun getInstance(): CompletionFinishedTypeComponent = service()
  }
}

/**
 * Calculator class for completion finish ratios.
 * Provides calculated metrics based on the underlying component data.
 */
internal class CompletionFinishTypeFeatures() {
  private val component
    get() = CompletionFinishedTypeComponent.getInstance()

  fun getSelectedRatio(): Double {
    val total = component.getTotalCount()
    val selected = component.getCountByType(CompletionFinishType.SELECTED)
    return safeDiv(selected.toLong(), total.toLong()) ?: 0.0
  }

  fun getInvalidatedRatio(): Double {
    val total = component.getTotalCount()
    val invalidated = component.getCountByType(CompletionFinishType.INVALIDATED)
    return safeDiv(invalidated.toLong(), total.toLong()) ?: 0.0
  }

  fun getExplicitCancelRatio(): Double {
    val total = component.getTotalCount()
    val explicitCancel = component.getCountByType(CompletionFinishType.EXPLICIT_CANCEL)
    return safeDiv(explicitCancel.toLong(), total.toLong()) ?: 0.0
  }

  // Use this function before calling `getSomeRatio` to avoid sending unnecessary 0.0
  fun hasCompletionStatistics(): Boolean = component.getTotalCount() > 0
}
