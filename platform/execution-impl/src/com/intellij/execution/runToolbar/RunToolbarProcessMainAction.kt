// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.runToolbar

import com.intellij.execution.Executor
import com.intellij.execution.ExecutorRegistryImpl
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.diagnostic.logger

private val LOG = logger<RunToolbarProcessMainAction>()

internal class RunToolbarProcessMainAction(process: RunToolbarProcess, executor: Executor) : RunToolbarProcessAction(process, executor) {
  init {
    templatePresentation.putClientProperty(ActionButtonWithText.SHORTCUT_SHOULD_SHOWN, true)
  }

  override fun actionPerformed(e: AnActionEvent) {
    e.project?.let { project ->
      if (canRun(e)) {
        getSelectedConfiguration(e)?.let {
          val slotManager = RunToolbarSlotManager.getInstance(project)
          val mainSlotData = slotManager.mainSlotData

          mainSlotData.environment?.let { environment ->
            ExecutionUtil.restart(environment)
          } ?: run {
            ExecutorRegistryImpl.RunnerHelper.run(project, it.configuration, it, e.dataContext, executor)
          }
        }
      }
    }
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    traceLog(LOG, e)
  }

  override fun getSelectedConfiguration(e: AnActionEvent): RunnerAndConfigurationSettings? {
    return e.project?.let {
      val slotManager = RunToolbarSlotManager.getInstance(it)
      slotManager.mainSlotData.configuration
    }
  }
}