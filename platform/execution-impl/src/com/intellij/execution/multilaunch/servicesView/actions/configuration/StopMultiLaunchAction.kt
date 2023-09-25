// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.multilaunch.servicesView.actions.configuration

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.execution.multilaunch.execution.ExecutionEngine
import com.intellij.execution.multilaunch.execution.MultiLaunchExecutionModel
import com.intellij.idea.ActionsBundle

internal class StopMultiLaunchAction(
  private val model: MultiLaunchExecutionModel
) : AnAction(ActionsBundle.message("action.multilaunch.StopMultiLaunchAction.text"), null, AllIcons.Actions.Suspend) {
  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun update(e: AnActionEvent) {
    val presentation = e.presentation
    presentation.isVisible = true
    presentation.isEnabled = model.isRunning()
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    ExecutionEngine.getInstance(project).stop(model.configuration)
  }
}
