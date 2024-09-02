// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere.statistics

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereFoundElementInfo
import com.intellij.ide.actions.searcheverywhere.SearchListener
import com.intellij.internal.statistic.utils.StartMoment
import com.intellij.util.TimeoutUtil
import java.util.concurrent.atomic.AtomicReference
import java.time.Duration

internal class SearchPerformanceTracker(private val startMoment: StartMoment?, private val tabIdProvider: () -> String) : SearchListener {
  private val initTime = System.nanoTime()

  private val currentSearch: AtomicReference<SearchPerformanceInfo?> = AtomicReference()

  private var firstSearch: FinishedSearchPerformanceInfo? = null
  private var lastSearch: FinishedSearchPerformanceInfo? = null
  private var wasCancelled = true

  override fun searchStarted(pattern: String, contributors: MutableCollection<out SearchEverywhereContributor<*>>) {
    val tab = tabIdProvider.invoke()
    currentSearch.set(if (pattern.isNotEmpty()) SearchPerformanceInfo(tab) else null)
  }

  override fun elementsAdded(list: MutableList<out SearchEverywhereFoundElementInfo>) {
    stop(true)
  }

  override fun elementsRemoved(list: MutableList<out SearchEverywhereFoundElementInfo>) {}

  override fun contributorWaits(contributor: SearchEverywhereContributor<*>) {}

  override fun contributorFinished(contributor: SearchEverywhereContributor<*>, hasMore: Boolean) {}

  override fun searchFinished(hasMoreContributors: MutableMap<SearchEverywhereContributor<*>, Boolean>) {
    stop(false)
  }

  private fun stop(fromAddingElements: Boolean) {
    currentSearch.getAndUpdate { null }?.stop(startMoment, fromAddingElements)?.let {
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

  fun popupIsClosedDueToSelection() {
    wasCancelled = false
  }

  fun isDialogCancelled() = wasCancelled
}

internal data class SearchPerformanceInfo(val tab: String) {
  private val startTime: Long = System.nanoTime()

  fun stop(startMoment: StartMoment?, fromAddingElements: Boolean): FinishedSearchPerformanceInfo {
    return FinishedSearchPerformanceInfo(tab,
                                         TimeoutUtil.getDurationMillis(startTime),
                                         if (fromAddingElements) startMoment?.getCurrentDuration() else null)
  }
}

internal data class FinishedSearchPerformanceInfo(
  val tab: String,
  val timeToFirstResult: Long,
  val durationToFirstResultFromTheStartMoment: Duration? = null,
)

internal data class SearchSessionPerformanceInfo(
  val firstSearch: FinishedSearchPerformanceInfo?, val lastSearch: FinishedSearchPerformanceInfo?, val duration: Long,
)
