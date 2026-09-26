// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.local

import com.intellij.lang.Language
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface LanguageUsageStatisticsProvider {
  fun getStatisticsForLanguage(language: Language): LanguageUsageStatistics

  /**
   * @return Map of languages (represented by their IDs) and their usage statistics
   */
  fun getStatistics(): Map<String, LanguageUsageStatistics>
  fun updateLanguageStatistics(language: Language)
}

@ApiStatus.Internal
data class LanguageUsageStatistics(val useCount: Int, val isMostUsed: Boolean,
                                   val lastUsed: Long, val isMostRecent: Boolean) {
  companion object {
    val NEVER_USED = LanguageUsageStatistics(0, false, Long.MAX_VALUE, false)
  }
}
