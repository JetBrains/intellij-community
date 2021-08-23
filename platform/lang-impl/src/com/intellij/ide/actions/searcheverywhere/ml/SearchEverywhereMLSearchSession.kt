// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere.ml

import com.intellij.ide.actions.searcheverywhere.ActionSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereFoundElementInfo
import com.intellij.ide.actions.searcheverywhere.SearchRestartReason
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereContextFeaturesProvider
import com.intellij.ide.util.gotoByName.GotoActionModel
import com.intellij.openapi.project.Project
import com.intellij.util.containers.ContainerUtil
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

internal class SearchEverywhereMLSearchSession(project: Project?, private val sessionId: Int) {
  private val itemIdProvider = SearchEverywhereMlItemIdProvider()
  private val sessionStartTime: Long = System.currentTimeMillis()

  // context features are calculated once per Search Everywhere session
  private val cachedContextInfo: SearchEverywhereMLContextInfo = SearchEverywhereMLContextInfo(project)

  // search state is updated on each typing, tab or setting change
  // element features & ML score are also re-calculated on each typing because some of them might change, e.g. matching degree
  private val currentSearchState: AtomicReference<SearchEverywhereMlSearchState?> = AtomicReference<SearchEverywhereMlSearchState?>()
  private val logger: SearchEverywhereMLStatisticsCollector = SearchEverywhereMLStatisticsCollector()

  fun onSearchRestart(project: Project?, previousElementsProvider: () -> List<SearchEverywhereFoundElementInfo>,
                      reason: SearchRestartReason,
                      tabId: String,
                      keysTyped: Int,
                      backspacesTyped: Int,
                      queryLength: Int) {
    val prevState = currentSearchState.getAndUpdate { prevState ->
      val startTime = System.currentTimeMillis()
      val searchReason = if (prevState == null) SearchRestartReason.SEARCH_STARTED else reason
      val nextSearchIndex = (prevState?.searchIndex ?: 0) + 1
      SearchEverywhereMlSearchState(sessionStartTime, startTime, nextSearchIndex, searchReason, tabId, keysTyped, backspacesTyped, queryLength)
    }

    if (prevState != null && isActionsTab(prevState.tabId)) {
      logger.onSearchRestarted(project, sessionId, prevState.searchIndex, itemIdProvider, cachedContextInfo, prevState, previousElementsProvider)
    }
  }

  fun onItemSelected(project: Project?, experimentStrategy: SearchEverywhereMlExperiment,
                     indexes: IntArray, closePopup: Boolean,
                     elementsProvider: () -> List<SearchEverywhereFoundElementInfo>) {
    val state = currentSearchState.get()
    if (state != null && isActionsTab(state.tabId)) {
      val orderByMl = orderedByMl(experimentStrategy, state.tabId)
      logger.onItemSelected(
        project, sessionId, state.searchIndex,
        experimentStrategy.experimentGroup, orderByMl,
        itemIdProvider, cachedContextInfo, state,
        indexes, closePopup, elementsProvider
      )
    }
  }

  fun onSearchFinished(project: Project?, experimentStrategy: SearchEverywhereMlExperiment,
                       elementsProvider: () -> List<SearchEverywhereFoundElementInfo>) {
    val state = currentSearchState.get()
    if (state != null && isActionsTab(state.tabId)) {
      val orderByMl = orderedByMl(experimentStrategy, state.tabId)
      logger.onSearchFinished(
        project, sessionId, state.searchIndex,
        experimentStrategy.experimentGroup, orderByMl,
        itemIdProvider, cachedContextInfo, state,
        elementsProvider
      )
    }
  }

  fun getMLWeight(contributor: SearchEverywhereContributor<*>, element: GotoActionModel.MatchedValue): Double {
    val state = currentSearchState.get()
    if (state != null && isActionsTab(state.tabId)) {
      val id = itemIdProvider.getId(element)
      return state.getMLWeight(id, element, contributor, cachedContextInfo)
    }
    return -1.0
  }

  private fun orderedByMl(experimentStrategy: SearchEverywhereMlExperiment, tabId: String): Boolean {
    return isActionsTab(tabId) && experimentStrategy.shouldOrderByMl()
  }

  private fun isActionsTab(tabId: String) = ActionSearchEverywhereContributor::class.java.simpleName == tabId
}

class SearchEverywhereMlItemIdProvider {
  private var idCounter = AtomicInteger(1)
  private val itemToId = ContainerUtil.createWeakMap<Any, Int>()

  @Synchronized
  fun getId(element: GotoActionModel.MatchedValue): Int {
    val key = if (element.value is GotoActionModel.ActionWrapper) element.value.action else element.value
    return itemToId.computeIfAbsent(key) { idCounter.getAndIncrement() }
  }
}

internal class SearchEverywhereMLContextInfo(project: Project?) {
  val features: Map<String, Any> by lazy {
    val featuresProvider = SearchEverywhereContextFeaturesProvider()
    return@lazy featuresProvider.getContextFeatures(project)
  }
}