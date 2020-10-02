// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.segmentedRunDebugWidget

import com.intellij.execution.Executor
import com.intellij.execution.ExecutorRegistry
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager

class RDCPillLabelGroup : ActionGroup(), DumbAware {
  private val executorRegistry = ExecutorRegistry.getInstance()

  fun getActions(project: Project): MutableList<AnAction> {
    val actions = mutableListOf<AnAction>()
    val toolWindowManager = ToolWindowManager.getInstance(project)
    RunDebugConfigManager.getInstance(project)?.getState()?.let {
      if (it.running) {
        executorRegistry.getExecutorById(RunDebugConfigManager.RUN_EXECUTOR_ID)?.let { executor ->
          actions.add(getLabelAction(executor, toolWindowManager))
        }
      }

      if(it.debugging) {
        executorRegistry.getExecutorById(RunDebugConfigManager.DEBUG_EXECUTOR_ID)?.let { executor ->
          if(actions.size>0) actions.add(Separator())
          actions.add(getLabelAction(executor, toolWindowManager))
        }
      }

      if(it.profiling) {
        executorRegistry.getExecutorById(RunDebugConfigManager.PROFILE_EXECUTOR_ID)?.let { executor ->
          if(actions.size>0) actions.add(Separator())
          actions.add(getLabelAction(executor, toolWindowManager))
        }
      }
    }
    return actions
  }

  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    val actions = mutableListOf<AnAction>()

    e?.project?.let { project ->
      actions.addAll(getActions(project))
    }

    return actions.toTypedArray()
  }


  private fun getLabelAction(executor: Executor, toolWindowManager: ToolWindowManager): AnAction {
    return object : AnAction(executor.actionName) {
      override fun displayTextInToolbar(): Boolean {
        return true
      }

      override fun actionPerformed(e: AnActionEvent) {
        toolWindowManager.getToolWindow(executor.toolWindowId)?.show()
      }

      override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = true
      }
    }
  }
}