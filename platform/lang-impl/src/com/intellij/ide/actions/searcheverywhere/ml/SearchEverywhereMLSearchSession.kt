// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere.ml

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereFoundElementInfo
import com.intellij.ide.actions.searcheverywhere.SearchRestartReason
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereContextFeaturesProvider
import com.intellij.ide.util.gotoByName.GotoActionModel
import com.intellij.openapi.project.Project
import com.intellij.util.containers.ContainerUtil
import java.util.concurrent.atomic.AtomicInteger

internal class SearchEverywhereMLSearchSession(project: Project?, private val sessionId: Int) {
  private val searchIndex = AtomicInteger(0)

  private val itemIdProvider = SearchEverywhereMlItemIdProvider()
  private val sessionStartTime: Long = System.currentTimeMillis()

  // context features are calculated once per Search Everywhere session
  private val cachedContextInfo: SearchEverywhereMLContextInfo by lazy {
    val featuresProvider = SearchEverywhereContextFeaturesProvider()
    return@lazy SearchEverywhereMLContextInfo(featuresProvider.getContextFeatures(project))
  }

  // element features & ML score are re-calculated on each typing because some of them might change (e.g. matching degree)
  private val cachedSearchState = mutableMapOf<Int, SearchEverywhereMlSearchState>()
  private val logger: SearchEverywhereMLStatisticsCollector = SearchEverywhereMLStatisticsCollector()

  fun onSearchRestart(previousElementsProvider: () -> List<SearchEverywhereFoundElementInfo>,
                      reason: SearchRestartReason,
                      tabId: String,
                      keysTyped: Int,
                      backspacesTyped: Int,
                      queryLength: Int) {
    val currentSearchIndex = searchIndex.get()
    val state = cachedSearchState.remove(currentSearchIndex)
    state?.let {
      logger.onSearchRestarted(sessionId, currentSearchIndex, itemIdProvider, cachedContextInfo, it, previousElementsProvider)
    }

    val startTime = System.currentTimeMillis()
    val nextSearchIndex = searchIndex.incrementAndGet()
    val searchReason = if (state == null) SearchRestartReason.SEARCH_STARTED else reason
    cachedSearchState[nextSearchIndex] = SearchEverywhereMlSearchState(
      sessionStartTime, startTime, searchReason, tabId, keysTyped, backspacesTyped, queryLength
    )
  }

  fun onItemSelected(indexes: IntArray, closePopup: Boolean, elementsProvider: () -> List<SearchEverywhereFoundElementInfo>) {
    val currentSearchIndex = searchIndex.get()
    val state = cachedSearchState[currentSearchIndex]
    state?.let {
      logger.onItemSelected(sessionId, currentSearchIndex, itemIdProvider, cachedContextInfo, it, indexes, closePopup, elementsProvider)
    }
  }

  fun onSearchFinished(elementsProvider: () -> List<SearchEverywhereFoundElementInfo>) {
    val currentSearchIndex = searchIndex.get()
    val state = cachedSearchState[currentSearchIndex]
    state?.let {
      logger.onSearchFinished(sessionId, currentSearchIndex, itemIdProvider, cachedContextInfo, it, elementsProvider)
    }
  }

  fun getMLWeight(contributor: SearchEverywhereContributor<*>, element: GotoActionModel.MatchedValue): Double {
    val state = cachedSearchState[searchIndex.get()]
    state?.let {
      val id = itemIdProvider.getId(element)
      return state.getMLWeight(id, element, contributor, cachedContextInfo)
    }
    return -1.0
  }
}

class SearchEverywhereMlItemIdProvider {
  private var idCounter = AtomicInteger(1)
  private val itemToId = ContainerUtil.createWeakMap<Any, Int>()

  fun getId(element: GotoActionModel.MatchedValue): Int {
    val key = if (element.value is GotoActionModel.ActionWrapper) element.value.action else element.value
    return itemToId.computeIfAbsent(key) { idCounter.getAndIncrement() }
  }
}