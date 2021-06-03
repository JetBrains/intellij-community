// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere.ml

import com.intellij.ide.actions.searcheverywhere.ActionSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.RebuildReason
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereFoundElementInfo
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereManagerImpl
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereContextFeaturesProvider
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereFeaturesProvider
import com.intellij.ide.actions.searcheverywhere.ml.logger.SearchEverywhereLogger
import com.intellij.internal.statistic.local.ActionsGlobalSummaryManager
import com.intellij.internal.statistic.local.ActionsLocalSummary
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

  private fun isActionOrAllTab(tabId: String): Boolean = ActionSearchEverywhereContributor::class.java.simpleName == tabId ||
                                                         SearchEverywhereManagerImpl.ALL_CONTRIBUTORS_GROUP_ID == tabId

  private fun reportElements(indexes: IntArray,
                             closePopup: Boolean,
                             keysTyped: Int,
                             backspacesTyped: Int,
                             symbolsInQuery: Int,
                             elementsProvider: () -> List<SearchEverywhereFoundElementInfo>,
                             tabId: String,
                             seSessionId: Int) {
    if (isLoggingEnabled(tabId)) {
      val foundElements = elementsProvider.invoke()
      NonUrgentExecutor.getInstance().execute {
        reportElements(indexes, closePopup, keysTyped, backspacesTyped, symbolsInQuery, foundElements, tabId, seSessionId)
      }
    }
  }

  private fun reportElements(indexes: IntArray,
                             closePopup: Boolean,
                             symbolsTyped: Int, backspacesTyped: Int,
                             symbolsInQuery: Int,
                             elements: List<SearchEverywhereFoundElementInfo>,
                             tabId: String,
                             seSessionId: Int) {
    val data = mutableMapOf<String, Any>()
    // put common data
    data.putAll(buildContextData(seSessionId, tabId, indexes, closePopup, elements.size, symbolsTyped, backspacesTyped))
    data.putAll(SearchEverywhereContextFeaturesProvider.getContextFeatures(myProject, myLastToolWindowId, symbolsInQuery))
    val currentTime = System.currentTimeMillis()

    // put data for every item
    val elementFeatureProvider = SearchEverywhereFeaturesProvider.getElementFeatureProvider()
    data[COLLECTED_RESULTS_DATA_KEY] = elements.take(REPORTED_ITEMS_LIMIT).map {
      elementFeatureProvider.getElementFeatures(it.priority, it.element, it.contributor, currentTime).toMap()
    }

    SearchEverywhereLogger.log(SESSION_FINISHED, data)
  }

  fun recordSelectedItem(indexes: IntArray,
                         closePopup: Boolean,
                         elementsProvider: () -> List<SearchEverywhereFoundElementInfo>,
                         keysTyped: Int,
                         backspacesTyped: Int,
                         textLength: Int,
                         tabId: String,
                         seSessionId: Int) {
    reportElements(indexes, closePopup, keysTyped, backspacesTyped, textLength, elementsProvider, tabId, seSessionId)
  }

  fun recordPopupClosed(elementsProvider: () -> List<SearchEverywhereFoundElementInfo>,
                        keysTyped: Int,
                        backspacesTyped: Int,
                        textLength: Int,
                        tabId: String,
                        seSessionId: Int) {
    reportElements(EMPTY, true, keysTyped, backspacesTyped, textLength, elementsProvider, tabId, seSessionId)
  }

  fun recordListRebuilt(seSessionId: Int, tabId: String, reason: RebuildReason, elements: List<SearchEverywhereFoundElementInfo>) {
    if (isLoggingEnabled(tabId)) {
      NonUrgentExecutor.getInstance().execute {
        val data = hashMapOf<String, Any>()
        data[SESSION_ID_LOG_DATA_KEY] = seSessionId
        data[REBUILD_REASON_KEY] = reason
        val currentTime = System.currentTimeMillis()
        data[CURRENT_TIME_KEY] = currentTime

        val elementFeatureProvider = SearchEverywhereFeaturesProvider.getElementFeatureProvider()
        data[REBUILD_ELEMENTS] = elements.take(REPORTED_ITEMS_LIMIT).map {
          elementFeatureProvider.getElementFeatures(it.priority, it.element, it.contributor, currentTime).toMap()
        }
        SearchEverywhereLogger.log(LIST_REBUILT, data)
      }
    }
  }

  private fun isLoggingEnabled(tabId: String): Boolean = myIsReporting && isActionOrAllTab(tabId)

  fun buildContextData(seSessionId: Int,
                       tabId: String,
                       indexes: IntArray,
                       closePopup: Boolean,
                       size: Int,
                       symbolsTyped: Int,
                       backspacesTyped: Int): Map<String, Any> {
    val data = hashMapOf<String, Any>()
    if (indexes.isNotEmpty()) {
      data[SELECTED_INDEXES_DATA_KEY] = indexes.map { it.toString() }
    }
    data[SE_TAB_ID_KEY] = tabId
    data[SESSION_ID_LOG_DATA_KEY] = seSessionId
    data[CLOSE_POPUP_KEY] = closePopup
    data[TOTAL_NUMBER_OF_ITEMS_DATA_KEY] = size
    data[TYPED_SYMBOL_KEYS] = symbolsTyped
    data[TYPED_BACKSPACES_DATA_KEY] = backspacesTyped
    return data
  }

  companion object {
    private val isExperimentModeEnabled: Boolean = ApplicationManager.getApplication().isEAP && StatisticsUploadAssistant.isSendAllowed()
    val localSummary: ActionsLocalSummary = ApplicationManager.getApplication().getService(ActionsLocalSummary::class.java)
    val globalSummary: ActionsGlobalSummaryManager = ApplicationManager.getApplication().getService(ActionsGlobalSummaryManager::class.java)

    private val EMPTY: IntArray = IntArray(0)

    private const val REPORTED_ITEMS_LIMIT = 50

    private const val SESSION_FINISHED = "sessionFinished"
    private const val LIST_REBUILT = "listRebuilt"
    private const val TYPED_SYMBOL_KEYS = "typedSymbolKeys"
    private const val TYPED_BACKSPACES_DATA_KEY = "typedBackspaces"
    private const val SESSION_ID_LOG_DATA_KEY = "sessionId"
    private const val SE_TAB_ID_KEY = "seTabId"
    private const val COLLECTED_RESULTS_DATA_KEY = "collectedItems"
    private const val REBUILD_ELEMENTS = "rebuildElements"
    private const val SELECTED_INDEXES_DATA_KEY = "selectedIndexes"

    // context features
    private const val CLOSE_POPUP_KEY = "closePopup"
    private const val TOTAL_NUMBER_OF_ITEMS_DATA_KEY = "totalItems"
    private const val REBUILD_REASON_KEY = "rebuildReason"
    private const val CURRENT_TIME_KEY = "currentTime"

    // action features
    internal const val ML_WEIGHT_KEY = "mlWeight"
    internal const val ADDITIONAL_DATA_KEY = "additionalData"
    internal const val CONTRIBUTOR_ID_KEY = "contributorId"
    internal const val ACTION_ID_KEY = "id"
  }
}