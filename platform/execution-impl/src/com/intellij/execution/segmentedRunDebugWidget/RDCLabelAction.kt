// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.segmentedRunDebugWidget

import com.intellij.execution.ExecutorRegistry
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ToolbarLabelAction
import com.intellij.openapi.wm.ToolWindowManager

abstract class RDCLabelAction(val executorID: String) : AnAction() {
  private val executorRegistry = ExecutorRegistry.getInstance()

  abstract fun isActive(state: RunDebugConfigManager.State): Boolean

  override fun displayTextInToolbar(): Boolean {
    return true
  }

  override fun update(e: AnActionEvent) {
    super.update(e)

    e.presentation.isEnabledAndVisible = e.project?.let {
      RunDebugConfigManager.getInstance(it)?.getState()?.let {
        if (isActive(it)) {
          executorRegistry.getExecutorById(executorID)?.let { executor ->
            e.presentation.text = executor.actionName
            true
          }
        } else false
      } ?: false
    } ?: false
  }

  override fun actionPerformed(e: AnActionEvent) {
    e.project?.let { project ->
      val toolWindowManager = ToolWindowManager.getInstance(project)
      executorRegistry.getExecutorById(executorID)?.let {
        toolWindowManager.getToolWindow(it.toolWindowId)?.let{ toolWindow ->
          toolWindow.show()
        }

      }
    }
  }
}

class RunningRDCLabelAction : RDCLabelAction(RunDebugConfigManager.RUN_EXECUTOR_ID) {
  override fun isActive(state: RunDebugConfigManager.State): Boolean {
    return state.running
  }
}

class DebuggingRDCLabelAction : RDCLabelAction(RunDebugConfigManager.DEBUG_EXECUTOR_ID) {
  override fun isActive(state: RunDebugConfigManager.State): Boolean {
    return state.debugging
  }
}

class ProfilingRDCLabelAction : RDCLabelAction(RunDebugConfigManager.PROFILE_EXECUTOR_ID) {
  override fun isActive(state: RunDebugConfigManager.State): Boolean {
    return state.profiling
  }
}


