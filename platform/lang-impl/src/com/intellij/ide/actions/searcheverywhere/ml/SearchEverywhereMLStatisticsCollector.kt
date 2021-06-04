// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere.ml

import com.intellij.ide.actions.searcheverywhere.ActionSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereFoundElementInfo
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereManagerImpl
import com.intellij.ide.actions.searcheverywhere.ml.SearchEverywhereMlSessionService.Companion.RECORDER_CODE
import com.intellij.ide.util.gotoByName.GotoActionModel
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.StatisticsEventLogProviderUtil
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.concurrency.NonUrgentExecutor
import kotlin.math.round

internal class SearchEverywhereMLStatisticsCollector {
  private val loggerProvider = StatisticsEventLogProviderUtil.getEventLogProvider(RECORDER_CODE)
  private val myIsReporting: Boolean = isEnabled()

  private fun isEnabled(): Boolean {
    val isExperimentModeEnabled = ApplicationManager.getApplication().isEAP && StatisticsUploadAssistant.isSendAllowed()
    if (isExperimentModeEnabled) {
      val percentage = Registry.get("statistics.mlse.report.percentage").asInteger() / 100.0
      return percentage >= 1 || Math.random() < percentage // only report a part of cases
    }
    return false
  }

  private fun isLoggingEnabled(tabId: String): Boolean = myIsReporting && isActionOrAllTab(tabId)

  private fun isActionOrAllTab(tabId: String): Boolean = ActionSearchEverywhereContributor::class.java.simpleName == tabId ||
                                                         SearchEverywhereManagerImpl.ALL_CONTRIBUTORS_GROUP_ID == tabId

  fun onItemSelected(seSessionId: Int, searchIndex: Int,
                     experimentGroup: Int, orderByMl: Boolean,
                     elementIdProvider: SearchEverywhereMlItemIdProvider,
                     context: SearchEverywhereMLContextInfo,
                     cache: SearchEverywhereMlSearchState,
                     selectedIndices: IntArray,
                     closePopup: Boolean,
                     elementsProvider: () -> List<SearchEverywhereFoundElementInfo>) {
    val data = arrayListOf<Pair<String, Any>>(
      CLOSE_POPUP_KEY to closePopup,
      EXPERIMENT_GROUP to experimentGroup,
      ORDER_BY_ML_GROUP to orderByMl
    )
    if (selectedIndices.isNotEmpty()) {
      data.add(SELECTED_INDEXES_DATA_KEY to selectedIndices.map { it.toString() })
    }
    reportElements(SESSION_FINISHED, seSessionId, searchIndex, elementIdProvider, context, cache, data, elementsProvider)
  }

  fun onSearchFinished(seSessionId: Int, searchIndex: Int,
                       experimentGroup: Int, orderByMl: Boolean,
                       elementIdProvider: SearchEverywhereMlItemIdProvider,
                       context: SearchEverywhereMLContextInfo,
                       cache: SearchEverywhereMlSearchState,
                       elementsProvider: () -> List<SearchEverywhereFoundElementInfo>) {
    val additional = listOf(
      CLOSE_POPUP_KEY to true,
      EXPERIMENT_GROUP to experimentGroup,
      ORDER_BY_ML_GROUP to orderByMl
    )
    reportElements(SESSION_FINISHED, seSessionId, searchIndex, elementIdProvider, context, cache, additional, elementsProvider)
  }

  fun onSearchRestarted(seSessionId: Int, searchIndex: Int,
                        elementIdProvider: SearchEverywhereMlItemIdProvider,
                        context: SearchEverywhereMLContextInfo,
                        cache: SearchEverywhereMlSearchState,
                        elementsProvider: () -> List<SearchEverywhereFoundElementInfo>) {
    reportElements(SEARCH_RESTARTED, seSessionId, searchIndex, elementIdProvider, context, cache, emptyList(), elementsProvider)
  }

  private fun reportElements(eventId: String,
                             seSessionId: Int, searchIndex: Int,
                             elementIdProvider: SearchEverywhereMlItemIdProvider,
                             context: SearchEverywhereMLContextInfo,
                             state: SearchEverywhereMlSearchState,
                             additional: List<Pair<String, Any>>,
                             elementsProvider: () -> List<SearchEverywhereFoundElementInfo>) {
    if (isLoggingEnabled(state.tabId)) {
      val elements = elementsProvider.invoke()
      NonUrgentExecutor.getInstance().execute {
        val data = hashMapOf<String, Any>()
        data[SESSION_ID_LOG_DATA_KEY] = seSessionId
        data[SEARCH_INDEX_DATA_KEY] = searchIndex
        data[TOTAL_NUMBER_OF_ITEMS_DATA_KEY] = elements.size
        data[SE_TAB_ID_KEY] = state.tabId
        data[SEARCH_START_TIME_KEY] = state.searchStartTime
        data[TYPED_SYMBOL_KEYS] = state.keysTyped
        data[TYPED_BACKSPACES_DATA_KEY] = state.backspacesTyped
        data[REBUILD_REASON_KEY] = state.searchStartReason
        data.putAll(additional)
        data.putAll(context.features)

        val actionManager = ActionManager.getInstance()
        data[COLLECTED_RESULTS_DATA_KEY] = elements.take(REPORTED_ITEMS_LIMIT).map {
          val result: HashMap<String, Any> = hashMapOf(
            CONTRIBUTOR_ID_KEY to it.contributor.searchProviderId
          )

          if (it.element is GotoActionModel.MatchedValue) {
            val elementId = elementIdProvider.getId(it.element)
            val itemInfo = state.getElementFeatures(elementId, it.element, it.contributor, state.queryLength)
            if (itemInfo.features.isNotEmpty()) {
              result[FEATURES_DATA_KEY] = itemInfo.features
            }

            state.getMLWeightIfDefined(elementId)?.let { score ->
              result[ML_WEIGHT_KEY] = roundDouble(score)
            }

            itemInfo.id.let { id ->
              result[ID_KEY] = id
            }

            if (it.element.value is GotoActionModel.ActionWrapper) {
              val action = it.element.value.action
              result[ACTION_ID_KEY] = actionManager.getId(action) ?: action.javaClass.name
            }
          }
          result
        }

        loggerProvider.logger.logAsync(GROUP, eventId, data, false)
      }
    }
  }

  companion object {
    private val GROUP = EventLogGroup("mlse.log", 2)
    private const val REPORTED_ITEMS_LIMIT = 100

    // events
    private const val SESSION_FINISHED = "sessionFinished"
    private const val SEARCH_RESTARTED = "searchRestarted"

    private const val ORDER_BY_ML_GROUP = "orderByMl"
    private const val EXPERIMENT_GROUP = "experimentGroup"

    // context fields
    private const val SE_TAB_ID_KEY = "seTabId"
    private const val CLOSE_POPUP_KEY = "closePopup"
    private const val SEARCH_START_TIME_KEY = "startTime"
    private const val REBUILD_REASON_KEY = "rebuildReason"
    private const val SESSION_ID_LOG_DATA_KEY = "sessionId"
    private const val SEARCH_INDEX_DATA_KEY = "searchIndex"
    private const val TYPED_SYMBOL_KEYS = "typedSymbolKeys"
    private const val TOTAL_NUMBER_OF_ITEMS_DATA_KEY = "totalItems"
    private const val TYPED_BACKSPACES_DATA_KEY = "typedBackspaces"
    private const val COLLECTED_RESULTS_DATA_KEY = "collectedItems"
    private const val SELECTED_INDEXES_DATA_KEY = "selectedIndexes"

    // item fields
    internal const val ID_KEY = "id"
    internal const val ACTION_ID_KEY = "actionId"
    internal const val FEATURES_DATA_KEY = "features"
    internal const val CONTRIBUTOR_ID_KEY = "contributorId"
    internal const val ML_WEIGHT_KEY = "mlWeight"

    private fun roundDouble(value: Double): Double {
      if (!value.isFinite()) return -1.0
      return round(value * 100000) / 100000
    }
  }
}