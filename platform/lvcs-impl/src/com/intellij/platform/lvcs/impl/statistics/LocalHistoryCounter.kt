// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lvcs.impl.statistics

import com.intellij.internal.statistic.IdeActivityDefinition
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project
import com.intellij.platform.lvcs.impl.ActivityScope

internal object LocalHistoryCounter : CounterUsagesCollector() {
  private val GROUP: EventLogGroup = EventLogGroup("local.history.counter", 3)

  private val KIND_FIELD = EventFields.Enum<Kind>("kind")
  private val IS_TOOL_WINDOW_UI_FIELD = EventFields.Boolean("is_toolwindow_ui")

  private val OPEN_EVENT = GROUP.registerEvent("opened", KIND_FIELD, IS_TOOL_WINDOW_UI_FIELD)
  private val ACTION_EVENT = GROUP.registerEvent("action.used", EventFields.Enum<ActionKind>("actionKind"), KIND_FIELD, IS_TOOL_WINDOW_UI_FIELD)
  private val FILTER_EVENT = GROUP.registerEvent("filter.used", KIND_FIELD, IS_TOOL_WINDOW_UI_FIELD)

  private val LOAD_REVISIONS_ACTIVITY = GROUP.registerIdeActivity(activityName = "load.items", startEventAdditionalFields = arrayOf(KIND_FIELD, IS_TOOL_WINDOW_UI_FIELD))
  private val LOAD_DIFFS_ACTIVITY = GROUP.registerIdeActivity(activityName = "load.diff", startEventAdditionalFields = arrayOf(KIND_FIELD, IS_TOOL_WINDOW_UI_FIELD))
  private val FILTER_ACTIVITY = GROUP.registerIdeActivity(activityName = "filter", startEventAdditionalFields = arrayOf(KIND_FIELD, IS_TOOL_WINDOW_UI_FIELD))

  override fun getGroup(): EventLogGroup = GROUP

  fun logLocalHistoryOpened(scope: ActivityScope) = OPEN_EVENT.log(scope.kind, true)
  fun logLocalHistoryOpened(kind: Kind) = OPEN_EVENT.log(kind, false)
  fun logActionInvoked(actionKind: ActionKind, scope: ActivityScope) = ACTION_EVENT.log(actionKind, scope.kind, true)
  fun logActionInvoked(actionKind: ActionKind, kind: Kind) = ACTION_EVENT.log(actionKind, kind, false)
  fun logFilterUsed(scope: ActivityScope) = FILTER_EVENT.log(scope.kind, true)
  fun logFilterUsed(kind: Kind) = FILTER_EVENT.log(kind, false)

  fun <R> logLoadItems(project: Project, scope: ActivityScope, function: () -> R): R {
    return LOAD_REVISIONS_ACTIVITY.log(project, scope.kind, true, function)
  }

  fun <R> logLoadItems(project: Project, kind: Kind, function: () -> R): R {
    return LOAD_REVISIONS_ACTIVITY.log(project, kind, false, function)
  }

  fun <R> logLoadDiff(project: Project, scope: ActivityScope, function: () -> R): R {
    return LOAD_DIFFS_ACTIVITY.log(project, scope.kind, true, function)
  }

  fun <R> logLoadDiff(project: Project, kind: Kind, function: () -> R): R {
    return LOAD_DIFFS_ACTIVITY.log(project, kind, false, function)
  }

  fun <R> logFilter(project: Project, scope: ActivityScope, function: () -> R): R {
    return FILTER_ACTIVITY.log(project, scope.kind, true, function)
  }

  fun <R> logFilter(project: Project, kind: Kind, function: () -> R): R {
    return FILTER_ACTIVITY.log(project, kind, false, function)
  }

  private fun <R> IdeActivityDefinition.log(project: Project, kind: Kind, isNewUi: Boolean, function: () -> R): R {
    val ideActivity = started(project) { listOf(KIND_FIELD.with(kind), IS_TOOL_WINDOW_UI_FIELD.with(isNewUi)) }
    try {
      return function()
    }
    finally {
      ideActivity.finished()
    }
  }

  private val ActivityScope.kind: Kind
    get() = when (this) {
      is ActivityScope.Directory -> Kind.Directory
      is ActivityScope.Selection -> Kind.Selection
      is ActivityScope.SingleFile -> Kind.File
      is ActivityScope.Files -> Kind.Files
      ActivityScope.Recent -> Kind.Recent
    }

  enum class Kind {
    Recent, Files, File, Directory, Selection
  }

  enum class ActionKind {
    RevertRevisions, RevertChanges, CreatePatch, Diff
  }
}