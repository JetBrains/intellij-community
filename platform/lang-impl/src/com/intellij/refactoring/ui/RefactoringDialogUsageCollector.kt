// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.ui

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project

internal object RefactoringDialogUsageCollector : CounterUsagesCollector() {
  private val GROUP = EventLogGroup("refactoring.dialog", 3)

  private val SELECTED = EventFields.Boolean("selected")
  private val CLASS_NAME = EventFields.Class("class_name")
  private val OPEN_IN_EDITOR_SAVED = GROUP.registerVarargEvent("open.in.editor.saved", SELECTED, CLASS_NAME, EventFields.PluginInfo)
  private val OPEN_IN_EDITOR_SHOWN = GROUP.registerVarargEvent("open.in.editor.shown", SELECTED, CLASS_NAME, EventFields.PluginInfo)

  @JvmStatic
  fun logOpenInEditorSaved(project: Project, selected: Boolean, clazz: Class<*>) {
    OPEN_IN_EDITOR_SAVED.log(project, SELECTED.with(selected), CLASS_NAME.with(clazz))
  }

  @JvmStatic
  fun logOpenInEditorShown(project: Project, selected: Boolean, clazz: Class<*>) {
    OPEN_IN_EDITOR_SHOWN.log(project, SELECTED.with(selected), CLASS_NAME.with(clazz))
  }

  override fun getGroup(): EventLogGroup {
    return GROUP
  }
}