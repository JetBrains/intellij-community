package com.intellij.execution.multilaunch.servicesView.actions.configuration

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.rd.util.launchBackground
import com.intellij.openapi.util.NlsActions
import com.intellij.execution.multilaunch.MultiLaunchConfiguration
import com.intellij.execution.multilaunch.execution.ExecutionMode
import com.intellij.execution.multilaunch.execution.ExecutionEngine
import com.intellij.openapi.rd.util.lifetime
import javax.swing.Icon

abstract class ExecuteMultiLaunchAction(
  private val configuration: MultiLaunchConfiguration,
  @NlsActions.ActionText text: String,
  icon: Icon,
  private val mode: ExecutionMode
) : AnAction(text, null, icon) {
  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    project.lifetime.launchBackground {
      ExecutionEngine.getInstance(project).execute(configuration, mode)
    }
  }
}