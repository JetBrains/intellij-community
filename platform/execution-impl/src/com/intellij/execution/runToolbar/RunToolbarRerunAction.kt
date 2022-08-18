// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.runToolbar

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.FakeRerunAction
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ShortcutSet
import com.intellij.openapi.project.DumbAware

internal class RunToolbarRerunAction : FakeRerunAction(),
                                       RTBarAction,
                                       DumbAware {

  override fun getRightSideType(): RTBarAction.Type = RTBarAction.Type.RIGHT_FLEXIBLE

  override fun checkMainSlotVisibility(state: RunToolbarMainSlotState): Boolean {
    return state == RunToolbarMainSlotState.PROCESS
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.text = ExecutionBundle.message("run.dashboard.rerun.action.name")
    e.presentation.isVisible = e.isActiveProcess()

    e.presentation.isEnabled = !e.isProcessTerminating()

    if (!RunToolbarProcess.isExperimentalUpdatingEnabled) {
      e.mainState()?.let {
        e.presentation.isVisible = e.presentation.isVisible && checkMainSlotVisibility(it)
      }
    }
  }

  override fun setShortcutSet(shortcutSet: ShortcutSet) {}

  override fun getEnvironment(event: AnActionEvent): ExecutionEnvironment? {
    return event.environment()
  }

  override fun getDescriptor(event: AnActionEvent): RunContentDescriptor? {
    return getEnvironment(event)?.contentToReuse
  }
}
