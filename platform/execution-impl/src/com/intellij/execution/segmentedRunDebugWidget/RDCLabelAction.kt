// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.segmentedRunDebugWidget

import com.intellij.execution.ExecutorRegistry
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager

internal abstract class RDCLabelAction(private val processID: String) : AnAction() {

  override fun displayTextInToolbar() = true

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.project?.let {
      val stateWidgetManager = StateWidgetManager.getInstance(it)
      stateWidgetManager.getProcessById(processID)?.name?.let {
        e.presentation.text = it
        stateWidgetManager.getActiveProcessesIDs().contains(processID)
      } ?: false
    } ?: false
  }

  override fun actionPerformed(e: AnActionEvent) {
    e.project?.let { project ->
      StateWidgetManager.getInstance(project).getProcessById(processID)?.let {
        ExecutorRegistry.getInstance().getExecutorById(it.executorId)?.let { executor ->
          val toolWindowManager = ToolWindowManager.getInstance(project)
          toolWindowManager.getToolWindow(executor.toolWindowId)?.show()
        }
      }
    }
  }
}

private class RunningRDCLabelAction : RDCLabelAction(ToolWindowId.RUN)

private class DebuggingRDCLabelAction : RDCLabelAction(ToolWindowId.DEBUG)