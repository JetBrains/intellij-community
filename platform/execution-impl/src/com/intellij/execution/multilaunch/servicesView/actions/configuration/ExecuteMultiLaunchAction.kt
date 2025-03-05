package com.intellij.execution.multilaunch.servicesView.actions.configuration

import com.intellij.execution.*
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.util.NlsActions
import com.intellij.execution.multilaunch.execution.ExecutionMode
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.openapi.wm.ToolWindowId
import javax.swing.Icon

abstract class ExecuteMultiLaunchAction(
  private val settings: RunnerAndConfigurationSettings,
  @NlsActions.ActionText text: String,
  icon: Icon,
  private val mode: ExecutionMode
) : AnAction(text, null, icon) {
  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    val executor = when (mode) {
      ExecutionMode.Run -> DefaultRunExecutor.getRunExecutorInstance()
      ExecutionMode.Debug -> ExecutorRegistry.getInstance().getExecutorById(ToolWindowId.DEBUG) ?: throw CantRunException("Debug executor is null")
    }
    ExecutionUtil.doRunConfiguration(settings, executor, null, null, null)
  }
}