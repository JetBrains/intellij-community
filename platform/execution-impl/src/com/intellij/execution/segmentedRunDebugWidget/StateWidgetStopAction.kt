// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.segmentedRunDebugWidget

import com.intellij.build.events.BuildEventsNls
import com.intellij.execution.ExecutionBundle
import com.intellij.execution.actions.StopAction
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import javax.swing.Icon

class StateWidgetStopAction : StopAction() {
  override fun update(e: AnActionEvent) {
    e.project?.let {
      val stateWidgetManager = StateWidgetManager.getInstance(it)
      if(stateWidgetManager.getExecutionsCount() == 0) {
        e.presentation.isEnabledAndVisible = false
        return
      }
      e.presentation.isEnabledAndVisible = true
    }

    super.update(e)
    e.presentation.isEnabledAndVisible = e.presentation.isEnabled && e.presentation.isVisible
  }

  override fun getActionIcon(e: AnActionEvent): Icon {
    e.project?.let { project ->
      val stateWidgetManager = StateWidgetManager.getInstance(project)
      if(stateWidgetManager.getActiveProcesses().size == 1) {
        stateWidgetManager.getActiveProcesses().firstOrNull()?.getStopIcon()?.let {
          return it
        }
      }
    }

    return super.getActionIcon(e)
  }

  override fun getDisplayName(project: Project?, descriptor: RunContentDescriptor?): @BuildEventsNls.Title String? {
    val itemName = super.getDisplayName(project, descriptor)

    return project?.let {  prj ->
      descriptor?.let { runContentDescriptor ->
        StateWidgetManager.getInstance(prj).getExecutionByExecutionId(runContentDescriptor.executionId)?.executor?.actionName?.let {
          ExecutionBundle.message("state.widget.stop.action.item.name", it, itemName)
        }
      }
    } ?: itemName
  }
}