// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere.statistics

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereFoundElementInfo
import com.intellij.ide.util.gotoByName.GotoActionModel
import com.intellij.ide.util.gotoByName.GotoActionModel.MatchedValue
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.eventLog.fus.SearchEverywhereLogger.log
import com.intellij.internal.statistic.eventLog.fus.SearchEverywhereSessionService
import com.intellij.internal.statistic.local.ActionSummary
import com.intellij.internal.statistic.local.ActionsGlobalSummaryManager
import com.intellij.internal.statistic.local.ActionsLocalSummary
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.util.registry.Registry


internal class SearchEverywhereMLStatisticsCollector {
  private val mySessionId = ServiceManager.getService(SearchEverywhereSessionService::class.java).incAndGet()
  private val myIsReporting: Boolean

  init {
    val percentage = Registry.get("statistics.mlse.report.percentage").asInteger() / 100.0
    myIsReporting = percentage >= 1 || Math.random() < percentage // only report a part of cases
  }

  fun reportSelectedElements(indexes: IntArray) {
    if (!myIsReporting) {
      return
    }
    val logData = FeatureUsageData()
    logData.addData(SESSION_ID_LOG_DATA_KEY, mySessionId)
    logData.addData(SELECTED_INDEXES_DATA_KEY, indexes.contentToString())
    log(SESSION_FINISHED, logData.build())
  }

  fun reportSessionEnded(symbolsTyped: Int, backspacesTyped: Int, symbolsInQuery: Int,
                         elements: List<SearchEverywhereFoundElementInfo>) {
    if (!myIsReporting) {
      return
    }
    val logData = FeatureUsageData()
    logData.addData(SESSION_ID_LOG_DATA_KEY, mySessionId)
    logData.addData(TOTAL_NUMBER_OF_ITEMS_DATA_KEY, elements.size)
    logData.addData(TYPED_SYMBOL_KEYS, symbolsTyped)
    logData.addData(TYPED_BACKSPACES_DATA_KEY, backspacesTyped)
    logData.addData(TOTAL_SYMBOLS_AMOUNT_DATA_KEY, symbolsInQuery)
    val data = logData.build()
    val localSummary = ServiceManager.getService(ActionsLocalSummary::class.java).getActionsStats()
    (data as? MutableMap)?.put(COLLECTED_RESULTS_DATA_KEY,
                               elements.take(REPORTED_ITEMS_LIMIT).map { getListItemsNames(it, localSummary).toMap() })
    log(DIALOG_CLOSED, data)
  }

  private fun getListItemsNames(item: SearchEverywhereFoundElementInfo, localSummary: Map<String, ActionSummary>): ItemInfo {
    val element = item.getElement()
    val contributorId = item.getContributor().searchProviderId
    if (element !is MatchedValue) { // not an action/option
      return ItemInfo(element.javaClass.name, contributorId, java.util.Map.of())
    }
    if (element.value !is GotoActionModel.ActionWrapper) { // an option (OptionDescriptor)
      return ItemInfo("", contributorId, java.util.Map.of(IS_ACTION_DATA_KEY, false))
    }
    return fillActionItemInfo(item, element.value, localSummary, contributorId)
  }

  private fun fillActionItemInfo(item: SearchEverywhereFoundElementInfo,
                                 element: GotoActionModel.ActionWrapper,
                                 localSummary: Map<String, ActionSummary>,
                                 contributorId: String): ItemInfo {
    val action = element.action
    val actionId = ActionManager.getInstance().getId(action)

    val data = mutableMapOf(
      IS_ACTION_DATA_KEY to true,
      PRIORITY_DATA_KEY to item.getPriority(),
      IS_TOGGLE_ACTION_DATA_KEY to (action is ToggleAction),
      MATCH_MODE_KEY to element.mode,
      IS_GROUP_KEY to element.isGroupAction
    )

    element.actionText?.let {
      data[TEXT_LENGTH_KEY] = it.length
    }

    element.groupName?.let {
      data[GROUP_LENGTH_KEY] = it.length
    }

    val presentation = element.presentation
    data[HAS_ICON_KEY] = presentation.icon != null
    data[IS_ENABLED_KEY] = presentation.isEnabled
    data[WEIGHT_KEY] = presentation.weight

    localSummary[actionId]?.let {
      data[TIME_SINCE_LAST_USAGE_DATA_KEY] = System.currentTimeMillis() - it.lastUsedTimestamp
    }

    val globalSummary = ServiceManager.getService(ActionsGlobalSummaryManager::class.java).getActionStatistics(actionId)
    globalSummary?.let {
      data[GLOBAL_USAGE_COUNT_DATA_KEY] = it.usagesCount
      data[USERS_RATIO_DATA_KEY] = it.usersRatio
      data[USAGES_PER_USER_RATIO_DATA_KEY] = it.usagesPerUserRatio
    }
    return ItemInfo(actionId, contributorId, data)
  }

  data class ItemInfo(val id: String, val contributorId: String, val additionalData: Map<String, Any>) {
    fun toMap(): Map<String, Any> {
      return mapOf("id" to id,
                   "contributorId" to contributorId,
                   "additionalData" to additionalData)
    }
  }

  companion object {
    private const val REPORTED_ITEMS_LIMIT = 50

    private const val DIALOG_CLOSED = "dialogClosed"
    private const val SESSION_FINISHED = "sessionFinished"
    private const val TYPED_SYMBOL_KEYS = "typedSymbolKeys"
    private const val TYPED_BACKSPACES_DATA_KEY = "typedBackspaces"
    private const val SESSION_ID_LOG_DATA_KEY = "sessionId"
    private const val COLLECTED_RESULTS_DATA_KEY = "collectedItems"
    private const val SELECTED_INDEXES_DATA_KEY = "selectedIndexes"

    // context features
    private const val TOTAL_SYMBOLS_AMOUNT_DATA_KEY = "totalSymbolsAmount"
    private const val TOTAL_NUMBER_OF_ITEMS_DATA_KEY = "totalItems"

    // action features
    private const val IS_ACTION_DATA_KEY = "isAction"
    private const val IS_TOGGLE_ACTION_DATA_KEY = "isToggleAction"
    private const val PRIORITY_DATA_KEY = "priority"
    private const val MATCH_MODE_KEY = "matchMode"
    private const val TEXT_LENGTH_KEY = "textLength"
    private const val IS_GROUP_KEY = "isGroup"
    private const val GROUP_LENGTH_KEY = "groupLength"
    private const val HAS_ICON_KEY = "withIcon"
    private const val IS_ENABLED_KEY = "isEnabled"
    private const val WEIGHT_KEY = "weight"

    private const val TIME_SINCE_LAST_USAGE_DATA_KEY = "timeSinceLastUsage"
    private const val GLOBAL_USAGE_COUNT_DATA_KEY = "localUsageCount"
    private const val USERS_RATIO_DATA_KEY = "usersRatio"
    private const val USAGES_PER_USER_RATIO_DATA_KEY = "usagesPerUserRatio"
  }
}