// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere.ml

import com.intellij.ide.actions.searcheverywhere.ActionSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereFoundElementInfo
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereManagerImpl
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereContextFeaturesProvider
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereFeaturesProvider
import com.intellij.ide.actions.searcheverywhere.ml.logger.SearchEverywhereLogger
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.concurrency.NonUrgentExecutor

internal class SearchEverywhereMLStatisticsCollector(val myProject: Project?) {
  private val myIsReporting: Boolean
  private val myLastToolWindowId: String?

  init {
    myIsReporting = isEnabled()
    // report tool windows' ids
    myLastToolWindowId = if (myProject != null) {
      val twm = ToolWindowManager.getInstance(myProject)
      var id: String? = null
      ApplicationManager.getApplication().invokeAndWait {
        id = twm.lastActiveToolWindowId
      }
      id
    }
    else {
      null
    }
  }

  private fun isEnabled(): Boolean {
    if (isExperimentModeEnabled) {
      val percentage = Registry.get("statistics.mlse.report.percentage").asInteger() / 100.0
      return percentage >= 1 || Math.random() < percentage // only report a part of cases
    }
    return false
  }

  private fun isLoggingEnabled(tabId: String?): Boolean = myIsReporting && (tabId == null || isActionOrAllTab(tabId))

  private fun isActionOrAllTab(tabId: String): Boolean = ActionSearchEverywhereContributor::class.java.simpleName == tabId ||
                                                         SearchEverywhereManagerImpl.ALL_CONTRIBUTORS_GROUP_ID == tabId

  fun onItemSelected(selectedIndices: IntArray,
                     closePopup: Boolean,
                     elementsProvider: () -> List<SearchEverywhereFoundElementInfo>,
                     state: SearchEverywhereSearchState,
                     seSessionId: Int) {
    val data = arrayListOf<Pair<String, Any>>(CLOSE_POPUP_KEY to closePopup)
    if (selectedIndices.isNotEmpty()) {
      data.add(SELECTED_INDEXES_DATA_KEY to selectedIndices.map { it.toString() })
    }
    reportElements(SESSION_FINISHED, seSessionId, state, data, elementsProvider)
  }

  fun onSearchFinished(elementsProvider: () -> List<SearchEverywhereFoundElementInfo>,
                       state: SearchEverywhereSearchState,
                       seSessionId: Int) {
    reportElements(SESSION_FINISHED, seSessionId, state, listOf(CLOSE_POPUP_KEY to true), elementsProvider)
  }

  fun onSearchRestarted(seSessionId: Int,
                        state: SearchEverywhereSearchState,
                        elementsProvider: () -> List<SearchEverywhereFoundElementInfo>) {
    reportElements(SEARCH_RESTARTED, seSessionId, state, emptyList(), elementsProvider)
  }

  private fun reportElements(eventId: String,
                             seSessionId: Int,
                             state: SearchEverywhereSearchState,
                             additional: List<Pair<String, Any>>,
                             elementsProvider: () -> List<SearchEverywhereFoundElementInfo>) {
    if (isLoggingEnabled(state.tabId)) {
      val elements = elementsProvider.invoke()
      NonUrgentExecutor.getInstance().execute {
        val data = hashMapOf<String, Any>()
        data[SESSION_ID_LOG_DATA_KEY] = seSessionId
        data[TOTAL_NUMBER_OF_ITEMS_DATA_KEY] = elements.size
        data[SE_TAB_ID_KEY] = state.tabId
        data[SEARCH_START_TIME_KEY] = state.startTime
        data[TYPED_SYMBOL_KEYS] = state.keysTyped
        data[TYPED_BACKSPACES_DATA_KEY] = state.backspacesTyped
        data[REBUILD_REASON_KEY] = state.searchStartReason
        data.putAll(additional)
        data.putAll(SearchEverywhereContextFeaturesProvider.getContextFeatures(myProject, myLastToolWindowId, state.queryLength))

        val currentTime = System.currentTimeMillis()
        val elementFeatureProvider = SearchEverywhereFeaturesProvider.getElementFeatureProvider()
        data[COLLECTED_RESULTS_DATA_KEY] = elements.take(REPORTED_ITEMS_LIMIT).map {
          elementFeatureProvider.getElementFeatures(it.element, it.contributor, currentTime).toMap()
        }

        SearchEverywhereLogger.log(eventId, data)
      }
    }
  }

  companion object {
    private val isExperimentModeEnabled: Boolean = ApplicationManager.getApplication().isEAP && StatisticsUploadAssistant.isSendAllowed()
    private const val REPORTED_ITEMS_LIMIT = 100

    // events
    private const val SESSION_FINISHED = "sessionFinished"
    private const val SEARCH_RESTARTED = "searchRestarted"

    // context fields
    private const val SE_TAB_ID_KEY = "seTabId"
    private const val CLOSE_POPUP_KEY = "closePopup"
    private const val SEARCH_START_TIME_KEY = "startTime"
    private const val REBUILD_REASON_KEY = "rebuildReason"
    private const val SESSION_ID_LOG_DATA_KEY = "sessionId"
    private const val TYPED_SYMBOL_KEYS = "typedSymbolKeys"
    private const val TOTAL_NUMBER_OF_ITEMS_DATA_KEY = "totalItems"
    private const val TYPED_BACKSPACES_DATA_KEY = "typedBackspaces"
    private const val COLLECTED_RESULTS_DATA_KEY = "collectedItems"
    private const val SELECTED_INDEXES_DATA_KEY = "selectedIndexes"

    // item fields
    internal const val ML_WEIGHT_KEY = "mlWeight"
  }
}