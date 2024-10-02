// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.ide.actions.searcheverywhere.statistics.SearchingProcessStatisticsCollector
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SearchProcessLogger : SearchAdapter() {

  private val reportedContributors = mutableSetOf<SearchEverywhereContributor<*>>()

  override fun elementsAdded(list: List<SearchEverywhereFoundElementInfo>): Unit = list.forEach { reportOnce(it.contributor) }

  override fun searchStarted(pattern: String,
                             contributors: MutableCollection<out SearchEverywhereContributor<*>>): Unit = reportedContributors.clear()

  private fun reportOnce(contributor: SearchEverywhereContributor<*>) {
    if (reportedContributors.add(contributor)) SearchingProcessStatisticsCollector.elementShown(contributor)
  }
}