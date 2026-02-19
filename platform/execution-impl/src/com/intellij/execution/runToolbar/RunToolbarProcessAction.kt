// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.runToolbar

import com.intellij.execution.Executor
import com.intellij.execution.ExecutorRegistryImpl
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.actions.ExecutorAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import javax.swing.Icon

internal open class RunToolbarProcessAction(
  override val process: RunToolbarProcess,
  @JvmField protected val executor: Executor,
) : ExecutorAction(executor), ExecutorRunToolbarAction, DumbAware {
  override fun displayTextInToolbar(): Boolean = true

  override fun getInformativeIcon(project: Project,
                                  selectedConfiguration: RunnerAndConfigurationSettings,
                                  e: AnActionEvent): Icon {
    return executor.icon
  }

  override fun actionPerformed(e: AnActionEvent) {
    e.project?.let { project ->
      if (canRun(e)) {
        getSelectedConfiguration(e)?.let {
          ExecutorRegistryImpl.RunnerHelper.run(project, it.configuration, it, e.dataContext, executor)
        }
      }
    }
  }

  override fun checkMainSlotVisibility(state: RunToolbarMainSlotState): Boolean {
    return state == RunToolbarMainSlotState.CONFIGURATION
  }

  override fun update(e: AnActionEvent) {
    e.presentation.text = executor.actionName
    e.presentation.isVisible = !e.isActiveProcess()

    if (!RunToolbarProcess.isExperimentalUpdatingEnabled) {
      e.mainState()?.let {
        e.presentation.isVisible = e.presentation.isVisible && checkMainSlotVisibility(it)
      }
    }

    if (e.presentation.isVisible) {
      e.presentation.isEnabled = canRun(e)
    }
  }

  override fun getSelectedConfiguration(e: AnActionEvent): RunnerAndConfigurationSettings? = e.configuration()

  protected fun canRun(e: AnActionEvent): Boolean {
    return e.project?.let { project ->
      return getSelectedConfiguration(e)?.let {
        ExecutorRegistryImpl.RunnerHelper.canRun(project, executor, it.configuration)
      } ?: false
    } ?: false
  }
}

internal class RunToolbarGroupProcessAction(process: RunToolbarProcess, executor: Executor) : RunToolbarProcessAction(process, executor) {
  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabledAndVisible = e.presentation.isEnabled
  }
}