// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere.statistics

import com.intellij.ide.actions.searcheverywhere.ActionSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereFoundElementInfo
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereManagerImpl
import com.intellij.ide.util.gotoByName.GotoActionModel
import com.intellij.ide.util.gotoByName.GotoActionModel.MatchedValue
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.eventLog.fus.SearchEverywhereLogger
import com.intellij.internal.statistic.eventLog.fus.SearchEverywhereLogger.log
import com.intellij.internal.statistic.eventLog.fus.SearchEverywhereSessionService
import com.intellij.internal.statistic.local.ActionSummary
import com.intellij.internal.statistic.local.ActionsGlobalSummaryManager
import com.intellij.internal.statistic.local.ActionsLocalSummary
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.concurrency.NonUrgentExecutor
import kotlin.math.round


internal class SearchEverywhereMLStatisticsCollector(val myProject: Project?) {
  private val mySessionId = ServiceManager.getService(SearchEverywhereSessionService::class.java).incAndGet()
  private val myIsReporting: Boolean

  init {
    myIsReporting = isEnabled()
  }

  private fun isEnabled(): Boolean {
    if (isExperimentModeEnabled) {
      val percentage = Registry.get("statistics.mlse.report.percentage").asInteger() / 100.0
      return percentage >= 1 || Math.random() < percentage // only report a part of cases
    }
    return false;
  }

  private fun isActionOrAllTab(tabId: String): Boolean = ActionSearchEverywhereContributor::class.java.simpleName == tabId ||
                                                         SearchEverywhereManagerImpl.ALL_CONTRIBUTORS_GROUP_ID == tabId

  private fun reportElements(indexes: IntArray,
                             closePopup: Boolean,
                             keysTyped: Int,
                             backspacesTyped: Int,
                             symbolsInQuery: Int,
                             elementsProvider: () -> List<SearchEverywhereFoundElementInfo>,
                             tabId: String) {
    if (myIsReporting && isActionOrAllTab(tabId)) {
      NonUrgentExecutor.getInstance().execute {
        reportElements(indexes, closePopup, keysTyped, backspacesTyped, symbolsInQuery, elementsProvider.invoke(), tabId)
      }
    }
  }

  private fun reportElements(indexes: IntArray,
                             closePopup: Boolean,
                             symbolsTyped: Int, backspacesTyped: Int,
                             symbolsInQuery: Int,
                             elements: List<SearchEverywhereFoundElementInfo>,
                             tabId: String) {
    val logData = SearchEverywhereLogger.newData()
    logData.addData(SESSION_ID_LOG_DATA_KEY, mySessionId)
    if (indexes.isNotEmpty()) {
      logData.addData(SELECTED_INDEXES_DATA_KEY, indexes.map { it.toString() })
    }
    logData.addData(CLOSE_POPUP_KEY, closePopup)
    logData.addData(TOTAL_NUMBER_OF_ITEMS_DATA_KEY, elements.size)
    logData.addData(TYPED_SYMBOL_KEYS, symbolsTyped)
    logData.addData(TYPED_BACKSPACES_DATA_KEY, backspacesTyped)
    logData.addData(TOTAL_SYMBOLS_AMOUNT_DATA_KEY, symbolsInQuery)
    logData.addData(SE_TAB_ID_KEY, tabId)

    val globalSummary = ServiceManager.getService(ActionsGlobalSummaryManager::class.java)
    val globalTotalStats = globalSummary.totalSummary
    val localSummary = ServiceManager.getService(ActionsLocalSummary::class.java)
    val localActionsStats = localSummary.getActionsStats()
    val localTotalStats = localSummary.getTotalStats()
    logData.addData(LOCAL_MAX_USAGE_COUNT_KEY, localTotalStats.maxUsageCount)
    logData.addData(LOCAL_MIN_USAGE_COUNT_KEY, localTotalStats.minUsageCount)
    logData.addData(GLOBAL_MAX_USAGE_COUNT_KEY, globalTotalStats.maxUsageCount)
    logData.addData(GLOBAL_MIN_USAGE_COUNT_KEY, globalTotalStats.minUsageCount)

    myProject?.let {
      // report tool windows' ids
      val twm = ToolWindowManager.getInstance(it)
      ApplicationManager.getApplication().invokeAndWait {
        twm.lastActiveToolWindowId?.let { id -> logData.addData(LAST_ACTIVE_TOOL_WINDOW_KEY, id) }
      }

      // report types of open files in editor: fileType -> amount
      val fem = FileEditorManager.getInstance(it)
      logData.addData(OPEN_FILE_TYPES_KEY, fem.openFiles.map { file -> file.fileType.name }.distinct())
    }

    val currentTime = System.currentTimeMillis()
    val data = logData.build()
    (data as? MutableMap)?.put(
      COLLECTED_RESULTS_DATA_KEY,
      elements.take(REPORTED_ITEMS_LIMIT).map {
        getListItemsNames(it, currentTime, globalSummary, localActionsStats).toMap()
      }
    )

    log(SESSION_FINISHED, data)
  }

  fun recordSelectedItem(indexes: IntArray,
                         closePopup: Boolean,
                         elementsProvider: () -> List<SearchEverywhereFoundElementInfo>,
                         keysTyped: Int,
                         backspacesTyped: Int,
                         textLength: Int,
                         tabId: String) {
    reportElements(indexes, closePopup, keysTyped, backspacesTyped, textLength, elementsProvider, tabId)
  }

  fun recordPopupClosed(elementsProvider: () -> List<SearchEverywhereFoundElementInfo>,
                        keysTyped: Int,
                        backspacesTyped: Int,
                        textLength: Int,
                        tabId: String) {
    reportElements(EMPTY, true, keysTyped, backspacesTyped, textLength, elementsProvider, tabId)
  }

  private fun getListItemsNames(item: SearchEverywhereFoundElementInfo,
                                currentTime: Long,
                                globalSummaryManager: ActionsGlobalSummaryManager,
                                localSummary: Map<String, ActionSummary>): ItemInfo {
    val element = item.getElement()
    val contributorId = item.getContributor()?.searchProviderId ?: "undefined"
    if (element !is MatchedValue) { // not an action/option
      return ItemInfo(null, contributorId, emptyMap())
    }
    if (element.value !is GotoActionModel.ActionWrapper) { // an option (OptionDescriptor)
      return ItemInfo(null, contributorId, hashMapOf(IS_ACTION_DATA_KEY to false))
    }
    return fillActionItemInfo(item, currentTime, element.value, globalSummaryManager, localSummary, contributorId)
  }

  private fun fillActionItemInfo(item: SearchEverywhereFoundElementInfo,
                                 currentTime: Long,
                                 element: GotoActionModel.ActionWrapper,
                                 globalSummaryManager: ActionsGlobalSummaryManager,
                                 localSummary: Map<String, ActionSummary>,
                                 contributorId: String): ItemInfo {
    val action = element.action
    val actionId = ActionManager.getInstance().getId(action) ?: action.javaClass.name

    val data = mutableMapOf(
      IS_ACTION_DATA_KEY to true,
      PRIORITY_DATA_KEY to item.getPriority(),
      IS_TOGGLE_ACTION_DATA_KEY to (action is ToggleAction),
      MATCH_MODE_KEY to element.mode,
      IS_GROUP_KEY to element.isGroupAction
    )

    element.actionText?.let {
      data[TEXT_LENGTH_KEY] = withUpperBound(it.length)
    }

    element.groupName?.let {
      data[GROUP_LENGTH_KEY] = withUpperBound(it.length)
    }

    val presentation = if (element.hasPresentation()) element.presentation else action.templatePresentation
    data[HAS_ICON_KEY] = presentation.icon != null
    data[IS_ENABLED_KEY] = presentation.isEnabled
    data[WEIGHT_KEY] = presentation.weight

    localSummary[actionId]?.let {
      data[TIME_SINCE_LAST_USAGE_DATA_KEY] = currentTime - it.lastUsedTimestamp
      data[LOCAL_USAGE_COUNT_DATA_KEY] = it.usageCount
    }

    val globalSummary = globalSummaryManager.getActionStatistics(actionId)
    globalSummary?.let {
      data[GLOBAL_USAGE_COUNT_KEY] = it.usagesCount
      data[USERS_RATIO_DATA_KEY] = roundDouble(it.usersRatio)
      data[USAGES_PER_USER_RATIO_DATA_KEY] = roundDouble(it.usagesPerUserRatio)
    }
    return ItemInfo(actionId, contributorId, data)
  }

  data class ItemInfo(val id: String?, val contributorId: String, val additionalData: Map<String, Any>) {
    fun toMap(): Map<String, Any> {
      val result: HashMap<String, Any> = hashMapOf(
        "contributorId" to contributorId
      )
      if (additionalData.isNotEmpty()) {
        result["additionalData"] = additionalData
      }
      id?.let {
        result += "id" to it
      }
      return result
    }
  }

  companion object {
    private val isExperimentModeEnabled: Boolean = ApplicationManager.getApplication().isEAP && StatisticsUploadAssistant.isSendAllowed()

    private val EMPTY: IntArray = IntArray(0)

    private const val REPORTED_ITEMS_LIMIT = 50

    private const val SESSION_FINISHED = "sessionFinished"
    private const val TYPED_SYMBOL_KEYS = "typedSymbolKeys"
    private const val TYPED_BACKSPACES_DATA_KEY = "typedBackspaces"
    private const val SESSION_ID_LOG_DATA_KEY = "sessionId"
    private const val COLLECTED_RESULTS_DATA_KEY = "collectedItems"
    private const val SELECTED_INDEXES_DATA_KEY = "selectedIndexes"

    // context features
    private const val CLOSE_POPUP_KEY = "closePopup"
    private const val TOTAL_SYMBOLS_AMOUNT_DATA_KEY = "totalSymbolsAmount"
    private const val TOTAL_NUMBER_OF_ITEMS_DATA_KEY = "totalItems"
    private const val LOCAL_MAX_USAGE_COUNT_KEY = "maxUsage"
    private const val LOCAL_MIN_USAGE_COUNT_KEY = "minUsage"
    private const val GLOBAL_MAX_USAGE_COUNT_KEY = "globalMaxUsage"
    private const val GLOBAL_MIN_USAGE_COUNT_KEY = "globalMinUsage"

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
    private const val LAST_ACTIVE_TOOL_WINDOW_KEY = "lastOpenToolWindow"
    private const val OPEN_FILE_TYPES_KEY = "openFileTypes"
    private const val SE_TAB_ID_KEY = "seTabId"

    private const val TIME_SINCE_LAST_USAGE_DATA_KEY = "timeSinceLastUsage"
    private const val LOCAL_USAGE_COUNT_DATA_KEY = "usage"
    private const val GLOBAL_USAGE_COUNT_KEY = "globalUsage"
    private const val USERS_RATIO_DATA_KEY = "usersRatio"
    private const val USAGES_PER_USER_RATIO_DATA_KEY = "usagesPerUserRatio"

    private fun withUpperBound(value: Int): Int {
      if (value > 100) return 101
      return value
    }

    private fun roundDouble(value: Double): Double {
      if (!value.isFinite()) return -1.0
      return round(value * 100000) / 100000
    }
  }
}