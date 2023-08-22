// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.runToolbar

import com.intellij.execution.actions.StopAction
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ShortcutSet
import com.intellij.openapi.diagnostic.Logger
import javax.swing.Icon

internal class RunToolbarMainMultipleStopAction : StopAction(),
                                                  RTBarAction {

  companion object {
    private val LOG = Logger.getInstance(RunToolbarMainMultipleStopAction::class.java)
  }

  override fun getRightSideType(): RTBarAction.Type = RTBarAction.Type.RIGHT_STABLE

  override fun checkMainSlotVisibility(state: RunToolbarMainSlotState): Boolean {
    return state == RunToolbarMainSlotState.INFO
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    super.update(e)
    val presentation = e.presentation
    presentation.isEnabledAndVisible = presentation.isEnabled && presentation.isVisible

    if (!RunToolbarProcess.isExperimentalUpdatingEnabled) {
      e.mainState()?.let {
        presentation.isEnabledAndVisible = presentation.isEnabledAndVisible && checkMainSlotVisibility(it)
      }
    }

    traceLog(LOG, e)
  }

  override fun setShortcutSet(shortcutSet: ShortcutSet) {}

  override fun getActionIcon(e: AnActionEvent): Icon {
    e.project?.let { project ->
      val activeProcesses = RunToolbarSlotManager.getInstance(project).activeProcesses
      if (activeProcesses.processes.size == 1) {
        activeProcesses.processes.keys.firstOrNull()?.getStopIcon()?.let {
          return it
        }
      }
    }

    return super.getActionIcon(e)
  }
}