// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.logs.statistics

import com.intellij.openapi.components.BaseState
import java.time.LocalDate

internal fun safeDiv(x: Long, y: Long): Double? = if (y == 0L) null else x.toDouble() / y

/**
 * An interface for DailyData classes that defines an update mechanism.
 * This method is called by the State class, and DailyData should handle it appropriately.
 * See examples in the Component classes.
 *
 * @param T the type of parameter used for updating
 */
internal interface ModifiableStorage<T> {
  fun update(param: T)
}

/**
 * Base class for user factor states that store daily data.
 * Provides common functionality for managing date-based data storage.
 */
internal abstract class UserFactorState<DailyData, T>(private val dailyDataFactory: () -> DailyData) : BaseState() where DailyData : BaseState, DailyData : ModifiableStorage<T> {

  /**
   * Gets or creates a DailyData entry for the current date.
   * Subclasses should override this method to return their specific DailyData type.
   *
   * @return The DailyData for the current date
   */
  private fun getDailyDataForToday(): DailyData {
    return dailyDataMap.getOrPut(getCurrentDate()) { dailyDataFactory() }
  }

  /**
   * Cleans up old data from the daily data map, keeping only the `MAX_DAYS_TO_STORE` recent entries.
   */
  private fun cleanupOldData() {
    if (dailyDataMap.size > UserStatisticConstants.MAX_DAYS_TO_STORE) {
      val sortedDates = dailyDataMap.keys.sortedBy {
        LocalDate.parse(it, UserStatisticConstants.DATE_FORMATTER)
      }
      val datesToRemove = sortedDates.take(sortedDates.size - UserStatisticConstants.MAX_DAYS_TO_STORE)
      datesToRemove.forEach { dailyDataMap.remove(it) }
    }
  }

  /**
   * Gets the current date formatted as a string.
   * Uses the standard date formatter defined in UserStatisticConstants.
   *
   * @return The current date as a formatted string
   */
  private fun getCurrentDate(): String {
    return LocalDate.now().format(UserStatisticConstants.DATE_FORMATTER)
  }

  /**
   * Map storing daily data entries, keyed by date string.
   */
  abstract var dailyDataMap: MutableMap<String, DailyData>

  /**
   * Increments the daily data counter for today using the provided parameter.
   *
   * This method:
   * 1. Retrieves (or creates) the daily data entry for the current date
   * 2. Delegates to the daily data's increment implementation
   * 3. Updates the modification count to track changes
   * 4. Performs cleanup of old data entries if necessary
   *
   * @param param the parameter to pass to the daily data's increment method
   */
  fun increment(param: T) {
    val todayData = getDailyDataForToday()
    todayData.update(param)
    incrementModificationCount()
    cleanupOldData()
  }
}
