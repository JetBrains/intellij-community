// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere.ml.features

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereFoundElementInfo
import com.intellij.ide.util.gotoByName.GotoActionModel
import com.intellij.internal.statistic.local.ActionsGlobalSummaryManager
import com.intellij.internal.statistic.local.ActionsLocalSummary
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import kotlin.math.round

object SearchEverywhereFeaturesProvider {
  val localSummary: ActionsLocalSummary = ApplicationManager.getApplication().getService(ActionsLocalSummary::class.java)
  val globalSummary: ActionsGlobalSummaryManager = ApplicationManager.getApplication().getService(ActionsGlobalSummaryManager::class.java)

  // context features
  private const val TOTAL_SYMBOLS_AMOUNT_DATA_KEY = "totalSymbolsAmount"
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
  internal const val ML_WEIGHT_KEY = "mlWeight"

  private const val TIME_SINCE_LAST_USAGE_DATA_KEY = "timeSinceLastUsage"
  private const val LOCAL_USAGE_COUNT_DATA_KEY = "usage"
  private const val GLOBAL_USAGE_COUNT_KEY = "globalUsage"
  private const val USERS_RATIO_DATA_KEY = "usersRatio"
  private const val USAGES_PER_USER_RATIO_DATA_KEY = "usagesPerUserRatio"
  internal const val ADDITIONAL_DATA_KEY = "additionalData"
  internal const val CONTRIBUTOR_ID_KEY = "contributorId"
  internal const val ACTION_ID_KEY = "id"

  @JvmStatic
  fun fillActionItemInfo(priority: Int,
                         currentTime: Long,
                         matchedValue: GotoActionModel.MatchedValue,
                         contributorId: String): ItemInfo {
    val wrapper = matchedValue.value as? GotoActionModel.ActionWrapper
    val data = mutableMapOf(
      IS_ACTION_DATA_KEY to (wrapper != null),
      PRIORITY_DATA_KEY to priority
    )

    if (wrapper == null) {
      // item is an option (OptionDescriptor)
      return ItemInfo(null, contributorId, data)
    }

    data[MATCH_MODE_KEY] = wrapper.mode
    data[IS_GROUP_KEY] = wrapper.isGroupAction
    val action = wrapper.action
    data[IS_TOGGLE_ACTION_DATA_KEY] = action is ToggleAction
    wrapper.actionText?.let {
      data[TEXT_LENGTH_KEY] = withUpperBound(it.length)
    }

    wrapper.groupName?.let {
      data[GROUP_LENGTH_KEY] = withUpperBound(it.length)
    }

    val presentation = if (wrapper.hasPresentation()) wrapper.presentation else action.templatePresentation
    data[HAS_ICON_KEY] = presentation.icon != null
    data[IS_ENABLED_KEY] = presentation.isEnabled
    data[WEIGHT_KEY] = presentation.weight


    val actionId = ActionManager.getInstance().getId(action) ?: action.javaClass.name
    localSummary.getActionsStats()[actionId]?.let {
      data[TIME_SINCE_LAST_USAGE_DATA_KEY] = currentTime - it.lastUsedTimestamp
      data[LOCAL_USAGE_COUNT_DATA_KEY] = it.usageCount
    }

    val globalSummary = globalSummary.getActionStatistics(actionId)
    globalSummary?.let {
      data[GLOBAL_USAGE_COUNT_KEY] = it.usagesCount
      data[USERS_RATIO_DATA_KEY] = roundDouble(it.usersRatio)
      data[USAGES_PER_USER_RATIO_DATA_KEY] = roundDouble(it.usagesPerUserRatio)
    }
    return ItemInfo(actionId, contributorId, data)
  }

  fun buildCommonFeaturesMap(symbolsInQuery: Int,
                             tabId: String,
                             lastToolwindowId: String?,
                             project: Project?): Map<String, Any> {
    val data = hashMapOf<String, Any>()
    data[TOTAL_SYMBOLS_AMOUNT_DATA_KEY] = symbolsInQuery
    data[SE_TAB_ID_KEY] = tabId

    val globalTotalStats = globalSummary.totalSummary
    val localTotalStats = localSummary.getTotalStats()
    data[LOCAL_MAX_USAGE_COUNT_KEY] = localTotalStats.maxUsageCount
    data[LOCAL_MIN_USAGE_COUNT_KEY] = localTotalStats.minUsageCount
    data[GLOBAL_MAX_USAGE_COUNT_KEY] = globalTotalStats.maxUsageCount
    data[GLOBAL_MIN_USAGE_COUNT_KEY] = globalTotalStats.minUsageCount

    project?.let {
      // report tool windows' ids
      lastToolwindowId?.let {
        data[LAST_ACTIVE_TOOL_WINDOW_KEY] = lastToolwindowId
      }

      // report types of open files in editor: fileType -> amount
      val fem = FileEditorManager.getInstance(it)
      data[OPEN_FILE_TYPES_KEY] = fem.openFiles.map { file -> file.fileType.name }.distinct()
    }
    return data
  }

  fun getListItemsNames(item: SearchEverywhereFoundElementInfo, currentTime: Long): ItemInfo {
    val element = item.getElement()
    val contributorId = item.getContributor()?.searchProviderId ?: "undefined"
    if (element !is GotoActionModel.MatchedValue) { // not an action/option
      return ItemInfo(null, contributorId, emptyMap())
    }
    return fillActionItemInfo(item.getPriority(), currentTime, element, contributorId)
  }

  private fun withUpperBound(value: Int): Int {
    if (value > 100) return 101
    return value
  }

  private fun roundDouble(value: Double): Double {
    if (!value.isFinite()) return -1.0
    return round(value * 100000) / 100000
  }

  data class ItemInfo(val id: String?, val contributorId: String, val additionalData: Map<String, Any>) {
    fun toMap(): Map<String, Any> {
      val result: HashMap<String, Any> = hashMapOf(
        CONTRIBUTOR_ID_KEY to contributorId
      )
      if (additionalData.isNotEmpty()) {
        result[ADDITIONAL_DATA_KEY] = additionalData
      }
      id?.let {
        result += ACTION_ID_KEY to it
      }
      return result
    }
  }
}