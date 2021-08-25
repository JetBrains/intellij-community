// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.runToolbar

import com.intellij.build.events.BuildEventsNls
import com.intellij.execution.actions.StopAction
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ShortcutSet
import com.intellij.openapi.project.Project
import javax.swing.Icon

class RunToolbarMainMultipleStopAction : StopAction(), RTBarAction {
  override fun getRightSideType(): RTBarAction.Type = RTBarAction.Type.RIGHT_STABLE

  override fun checkMainSlotVisibility(state: RunToolbarMainSlotState): Boolean {
    return state == RunToolbarMainSlotState.INFO
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabledAndVisible = e.presentation.isEnabled && e.presentation.isVisible

    if (!RunToolbarProcess.experimentalUpdating()) {
      e.mainState()?.let {
        e.presentation.isEnabledAndVisible = e.presentation.isEnabledAndVisible && checkMainSlotVisibility(it)
      }
    }
  }

  override fun setShortcutSet(shortcutSet: ShortcutSet) {}

 override fun getActionIcon(e: AnActionEvent): Icon {
    e.project?.let { project ->
      val activeProcesses = RunToolbarSlotManager.getInstance(project).activeProcesses
      if(activeProcesses.processes.size == 1) {
        activeProcesses.processes.keys.firstOrNull()?.getStopIcon()?.let {
          return it
        }
      }
    }

    return super.getActionIcon(e)
  }

  override fun getDisplayName(project: Project?, descriptor: RunContentDescriptor?): @BuildEventsNls.Title String? {
    val itemName = super.getDisplayName(project, descriptor)

    return /*project?.let {  prj ->
      descriptor?.let { runContentDescriptor ->
        StateWidgetManager.getInstance(prj).getExecutionByExecutionId(runContentDescriptor.executionId)?.executor?.actionName?.let {
          ExecutionBundle.message("state.widget.stop.action.item.name", it, itemName)
        }
      }
    } ?: */itemName
  }
}