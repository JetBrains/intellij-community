// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere.ml

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereFoundElementInfo
import com.intellij.ide.actions.searcheverywhere.ml.SearchEverywhereMlSessionService.Companion.RECORDER_CODE
import com.intellij.ide.util.gotoByName.GotoActionModel
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.StatisticsEventLogProviderUtil
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.NonUrgentExecutor
import kotlin.math.round

internal class SearchEverywhereMLStatisticsCollector {
  private val loggerProvider = StatisticsEventLogProviderUtil.getEventLogProvider(RECORDER_CODE)

  fun onItemSelected(project: Project?, seSessionId: Int, searchIndex: Int,
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
    reportElements(project, SESSION_FINISHED, seSessionId, searchIndex, elementIdProvider, context, cache, data, selectedIndices, elementsProvider)
  }

  fun onSearchFinished(project: Project?, seSessionId: Int, searchIndex: Int,
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
    reportElements(project, SESSION_FINISHED, seSessionId, searchIndex, elementIdProvider, context, cache, additional, EMPTY_ARRAY, elementsProvider)
  }

  fun onSearchRestarted(project: Project?, seSessionId: Int, searchIndex: Int,
                        elementIdProvider: SearchEverywhereMlItemIdProvider,
                        context: SearchEverywhereMLContextInfo,
                        cache: SearchEverywhereMlSearchState,
                        elementsProvider: () -> List<SearchEverywhereFoundElementInfo>) {
    reportElements(project, SEARCH_RESTARTED, seSessionId, searchIndex, elementIdProvider, context, cache, emptyList(), EMPTY_ARRAY, elementsProvider)
  }

  private fun reportElements(project: Project?, eventId: String,
                             seSessionId: Int, searchIndex: Int,
                             elementIdProvider: SearchEverywhereMlItemIdProvider,
                             context: SearchEverywhereMLContextInfo,
                             state: SearchEverywhereMlSearchState,
                             additional: List<Pair<String, Any>>,
                             selectedElements: IntArray,
                             elementsProvider: () -> List<SearchEverywhereFoundElementInfo>) {
    val elements = elementsProvider.invoke()
    NonUrgentExecutor.getInstance().execute {
      val data = hashMapOf<String, Any>()
      data[PROJECT_OPENED_KEY] = project != null
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

      if (selectedElements.isNotEmpty()) {
        data[SELECTED_INDEXES_DATA_KEY] = selectedElements.map { it.toString() }
        data[SELECTED_ELEMENTS_DATA_KEY] = selectedElements.map {
          if (it < elements.size) {
            val element = elements[it].element
            if (element is GotoActionModel.MatchedValue) {
              return@map elementIdProvider.getId(element)
            }
          }
          return@map -1
        }
      }

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

  companion object {
    private val GROUP = EventLogGroup("mlse.log", 5)
    private val EMPTY_ARRAY = IntArray(0)
    private const val REPORTED_ITEMS_LIMIT = 100

    // events
    private const val SESSION_FINISHED = "sessionFinished"
    private const val SEARCH_RESTARTED = "searchRestarted"

    private const val ORDER_BY_ML_GROUP = "orderByMl"
    private const val EXPERIMENT_GROUP = "experimentGroup"

    // context fields
    private const val PROJECT_OPENED_KEY = "projectOpened"
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
    private const val SELECTED_ELEMENTS_DATA_KEY = "selectedIds"

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