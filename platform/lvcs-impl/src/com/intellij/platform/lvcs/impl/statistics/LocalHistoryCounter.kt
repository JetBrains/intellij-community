// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lvcs.impl.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.platform.lvcs.impl.ActivityScope

object LocalHistoryCounter : CounterUsagesCollector() {
  private val GROUP: EventLogGroup = EventLogGroup("local.history.counter", 1)

  private val KIND_FIELD = EventFields.Enum<Kind>("kind")
  private val IS_TOOL_WINDOW_UI_FIELD = EventFields.Boolean("is_toolwindow_ui")

  private val OPEN_EVENT = GROUP.registerEvent("event.open", KIND_FIELD, IS_TOOL_WINDOW_UI_FIELD)
  private val ACTION_EVENT = GROUP.registerEvent("event.action", EventFields.Enum<ActionKind>("actionKind"), KIND_FIELD, IS_TOOL_WINDOW_UI_FIELD)
  private val FILTER_EVENT = GROUP.registerEvent("event.filter", KIND_FIELD, IS_TOOL_WINDOW_UI_FIELD)

  override fun getGroup(): EventLogGroup = GROUP

  fun logLocalHistoryOpened(scope: ActivityScope) = OPEN_EVENT.log(scope.kind, true)
  fun logLocalHistoryOpened(kind: Kind) = OPEN_EVENT.log(kind, false)
  fun logActionInvoked(actionKind: ActionKind, scope: ActivityScope) = ACTION_EVENT.log(actionKind, scope.kind, true)
  fun logActionInvoked(actionKind: ActionKind, kind: Kind) = ACTION_EVENT.log(actionKind, kind, false)
  fun logFilterUsed(scope: ActivityScope) = FILTER_EVENT.log(scope.kind, true)
  fun logFilterUsed(kind: Kind) = FILTER_EVENT.log(kind, false)

  private val ActivityScope.kind: Kind
    get() = when (this) {
      is ActivityScope.Directory -> Kind.Directory
      is ActivityScope.Selection -> Kind.Selection
      is ActivityScope.SingleFile -> Kind.File
      ActivityScope.Recent -> Kind.Recent
    }

  enum class Kind {
    Recent, File, Directory, Selection
  }

  enum class ActionKind {
    RevertRevisions, RevertChanges, CreatePatch, Diff
  }
}