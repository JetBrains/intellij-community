// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere.ml

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereFoundElementInfo
import com.intellij.ide.actions.searcheverywhere.SearchRestartReason
import com.intellij.openapi.project.Project

internal class SearchEverywhereMLSearchSession(val sessionId: Int, private var state: SearchEverywhereSearchState? = null) {
  private val logger: SearchEverywhereMLStatisticsCollector = SearchEverywhereMLStatisticsCollector(null)
  private val cache: SearchEverywhereMLCache = SearchEverywhereMLCache(sessionId)

  fun onSearchRestart(previousElementsProvider: () -> List<SearchEverywhereFoundElementInfo>,
                      reason: SearchRestartReason,
                      tabId: String,
                      keysTyped: Int,
                      backspacesTyped: Int,
                      queryLength: Int) {
    state?.let {
      //TODO: load features from cache if they are already calculated
      logger.onSearchRestarted(sessionId, it, previousElementsProvider)
    }

    val searchReason = if (state == null) SearchRestartReason.SEARCH_STARTED else reason
    state = SearchEverywhereSearchState(System.currentTimeMillis(), searchReason, tabId, keysTyped, backspacesTyped, queryLength)
  }

  fun onItemSelected(indexes: IntArray, closePopup: Boolean, elementsProvider: () -> List<SearchEverywhereFoundElementInfo>) {
    state?.let {
      logger.onItemSelected(indexes, closePopup, elementsProvider, it, sessionId)
    }
  }

  fun onSearchFinished(elementsProvider: () -> List<SearchEverywhereFoundElementInfo>) {
    state?.let {
      logger.onSearchFinished(elementsProvider, it, sessionId)
    }
  }

  fun getMLWeight(project: Project?, contributor: SearchEverywhereContributor<*>, element: Any, queryLength: Int): Double {
    return cache.getMLWeight(element, contributor, project, queryLength)
  }
}