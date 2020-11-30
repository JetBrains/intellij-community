// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.segmentedRunDebugWidget

import com.intellij.execution.ExecutorRegistry
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.ToolWindowManager

internal abstract class RDCLabelAction(private val executorID: String) : AnAction() {
  abstract fun isActive(state: RunDebugConfigManager.State): Boolean

  override fun displayTextInToolbar() = true

  override fun update(e: AnActionEvent) {
    super.update(e)

    e.presentation.isEnabledAndVisible = e.project?.let { project ->
      RunDebugConfigManager.getInstance(project).getState().let {
        if (isActive(it)) {
          ExecutorRegistry.getInstance().getExecutorById(executorID)?.let { executor ->
            e.presentation.text = executor.actionName
            true
          }
        }
        else false
      } ?: false
    } ?: false
  }

  override fun actionPerformed(e: AnActionEvent) {
    e.project?.let { project ->
      val toolWindowManager = ToolWindowManager.getInstance(project)
      ExecutorRegistry.getInstance().getExecutorById(executorID)?.let {
        toolWindowManager.getToolWindow(it.toolWindowId)?.show()
      }
    }
  }
}

private class RunningRDCLabelAction : RDCLabelAction(RunDebugConfigManager.RUN_EXECUTOR_ID) {
  override fun isActive(state: RunDebugConfigManager.State): Boolean {
    return state.running
  }
}

private class DebuggingRDCLabelAction : RDCLabelAction(RunDebugConfigManager.DEBUG_EXECUTOR_ID) {
  override fun isActive(state: RunDebugConfigManager.State): Boolean {
    return state.debugging
  }
}

private class ProfilingRDCLabelAction : RDCLabelAction(RunDebugConfigManager.PROFILE_EXECUTOR_ID) {
  override fun isActive(state: RunDebugConfigManager.State): Boolean {
    return state.profiling
  }
}