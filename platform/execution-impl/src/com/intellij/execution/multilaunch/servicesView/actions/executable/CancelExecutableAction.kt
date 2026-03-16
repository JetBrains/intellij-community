package com.intellij.execution.multilaunch.servicesView.actions.executable

import com.intellij.execution.multilaunch.execution.ExecutableExecutionModel
import com.intellij.execution.multilaunch.execution.ExecutionEngine
import com.intellij.execution.multilaunch.execution.ExecutionStatus
import com.intellij.execution.multilaunch.execution.MultiLaunchExecutionModel
import com.intellij.icons.AllIcons
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

internal class CancelExecutableAction(
  private val configurationModel: MultiLaunchExecutionModel,
  private val executableModel: ExecutableExecutionModel
) : AnAction(ActionsBundle.message("action.multilaunch.CancelExecutableAction.text"), null, AllIcons.RunConfigurations.TestTerminated) {
  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = executableModel.status.value == ExecutionStatus.Waiting
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    ExecutionEngine.getInstance(project).stop(configurationModel.configuration, executableModel.descriptor.executable)
  }
}

