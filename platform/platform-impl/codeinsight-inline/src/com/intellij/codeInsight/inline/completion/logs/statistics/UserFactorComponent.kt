// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.logs.statistics

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.SimplePersistentStateComponent
import java.time.LocalDate


internal fun safeDiv(x: Long, y: Long): Double? = if (y == 0L) null else x.toDouble() / y
/**
 * Base class for user factor components that store daily data.
 * Provides common functionality for managing date-based data storage.
 *
 * @param T The state type that extends BaseState
 */
internal abstract class UserFactorComponent<T : BaseState>(initialState: T) : SimplePersistentStateComponent<T>(initialState) {
  
  /**
   * Gets the daily data map from the state.
   * This method must be implemented by subclasses to provide access to their specific dailyData map.
   *
   * @return The mutable map containing daily data
   */
  protected abstract fun getDailyDataMap(): MutableMap<String, *>
  
  /**
   * Cleans up old data from the daily data map, keeping only the most recent entries.
   * Removes entries that exceed the maximum number of days to store.
   */
  protected fun cleanupOldData() {
    val dailyData = getDailyDataMap()
    if (dailyData.size > UserStatisticConstants.MAX_DAYS_TO_STORE) {
      val sortedDates = dailyData.keys.sortedBy {
        LocalDate.parse(it, UserStatisticConstants.DATE_FORMATTER)
      }
      val datesToRemove = sortedDates.take(sortedDates.size - UserStatisticConstants.MAX_DAYS_TO_STORE)
      datesToRemove.forEach { dailyData.remove(it) }
    }
  }
  
  /**
   * Gets the current date formatted as a string.
   * Uses the standard date formatter defined in UserStatisticConstants.
   *
   * @return The current date as a formatted string
   */
  protected fun getCurrentDate(): String {
    return LocalDate.now().format(UserStatisticConstants.DATE_FORMATTER)
  }
}