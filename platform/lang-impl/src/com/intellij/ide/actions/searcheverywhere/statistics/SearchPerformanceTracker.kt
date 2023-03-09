// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere.statistics

import com.intellij.util.TimeoutUtil
import java.util.concurrent.atomic.AtomicReference

internal class SearchPerformanceTracker {
  private val initTime = System.nanoTime()

  private val currentSearch: AtomicReference<SearchPerformanceInfo?> = AtomicReference()

  private var firstSearch: FinishedSearchPerformanceInfo? = null
  private var lastSearch: FinishedSearchPerformanceInfo? = null

  fun start(tab: String, query: String) {
    currentSearch.set(if (query.isNotEmpty()) SearchPerformanceInfo(tab) else null)
  }

  fun stop() {
    currentSearch.getAndUpdate { null }?.stop()?.let {
      synchronized(this) {
        if (firstSearch == null) {
          firstSearch = it
        }
        lastSearch = it
      }
    }
  }

  fun getPerformanceInfo(): SearchSessionPerformanceInfo {
    val totalDuration = TimeoutUtil.getDurationMillis(initTime)
    synchronized(this) {
      return SearchSessionPerformanceInfo(firstSearch, lastSearch, totalDuration)
    }
  }
}

internal data class SearchPerformanceInfo(val tab: String) {
  private val startTime: Long = System.nanoTime()

  fun stop(): FinishedSearchPerformanceInfo {
    return FinishedSearchPerformanceInfo(tab, TimeoutUtil.getDurationMillis(startTime))
  }
}

internal data class FinishedSearchPerformanceInfo(val tab: String, val timeToFirstResult: Long)

internal data class SearchSessionPerformanceInfo(
  val firstSearch: FinishedSearchPerformanceInfo?, val lastSearch: FinishedSearchPerformanceInfo?, val duration: Long
)
