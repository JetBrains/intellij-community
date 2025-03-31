// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.logs.statistics

import java.time.format.DateTimeFormatter
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Constants used across user statistic components.
 */
internal object UserStatisticConstants {
  /**
   * The name of the storage file used for persisting statistical data related to user-dependent features
   * in inline code completions.
   */
  const val STORAGE_FILE_NAME: String = "inline.factors.completion.xml"

  /**
   * Maximum number of days to store in the daily data maps.
   * Should be balanced between capturing user behavior and the ability to vary.
   */
  const val MAX_DAYS_TO_STORE: Int = 10

  /**
   * Global acceptance rate used for smoothing calculations.
   * This value represents a baseline acceptance rate for new users.
   * Taken based on Inline Completion Dashboard
   */
  const val GLOBAL_ACCEPTANCE_RATE: Double = 0.3

  /**
   * How many "entries" with global value we add for smoothing calculations.
   * Higher values give more weight to the global rate vs. user-specific data.
   */
  const val GLOBAL_ALPHA: Int = 10

  /**
   * Maximum time between typing events to be considered valid for statistics.
   * Events with longer intervals are excluded as they likely represent breaks in typing.
   */
  val MAX_TYPING_INTERVAL: Duration = 10.seconds

  /**
   * Standard date formatter used across components for consistent date handling.
   */
  val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
}