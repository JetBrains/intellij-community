// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.runToolbar

import com.intellij.execution.ExecutionTargetManager
import com.intellij.execution.Executor
import com.intellij.execution.ExecutorRegistryImpl
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.compound.SettingsAndEffectiveTarget
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import javax.swing.Icon

open class RunToolbarProcessAction(override val process: RunToolbarProcess, val executor: Executor) : ExecutorRegistryImpl.ExecutorAction(executor), ExecutorRunToolbarAction, DumbAware {

  override fun displayTextInToolbar(): Boolean {
    return true
  }

  override fun getInformativeIcon(project: Project, selectedConfiguration: RunnerAndConfigurationSettings): Icon {
    return executor.icon
  }

  override fun actionPerformed(e: AnActionEvent) {
    e.project?.let { project ->
      if (canRun(e)) {
        e.configuration()?.let {
          e.addWaitingForAProcess(executor.id)
          ExecutorRegistryImpl.RunnerHelper.run(project, it.configuration, it, e.dataContext, executor)
        }
      }
    }
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.text = executor.actionName
    e.presentation.isVisible = e.project?.let {
      e.presentation.isVisible && if (e.isItRunToolbarMainSlot()) {
        val slotManager = RunToolbarSlotManager.getInstance(it)
        (e.isOpened() && !e.isActiveProcess() || !slotManager.getState().isActive())
      }
      else !e.isActiveProcess()
    } ?: false

    e.presentation.isEnabled = e.project?.let {
      canRun(e)
    } ?: false
  }

  override fun getSelectedConfiguration(e: AnActionEvent): RunnerAndConfigurationSettings? {
    return e.configuration()
  }

  private fun canRun(e: AnActionEvent): Boolean {
    return e.project?.let { project->
      val activeTarget = ExecutionTargetManager.getActiveTarget(project)
      return e.configuration()?.let {

        val target = SettingsAndEffectiveTarget(it.configuration, activeTarget)

        val canRun = ExecutorRegistryImpl.RunnerHelper.canRun(project, listOf(target), executor)
        canRun

      } ?: false
    } ?: false
  }
}

class RunToolbarGroupProcessAction(process: RunToolbarProcess, executor: Executor) : RunToolbarProcessAction(process, executor) {
  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabledAndVisible = e.presentation.isEnabled
  }
}