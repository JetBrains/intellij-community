package com.intellij.execution.multilaunch

import com.intellij.execution.CantRunException
import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.dashboard.RunDashboardManager
import com.intellij.execution.impl.statistics.RunConfigurationUsageTriggerCollector
import com.intellij.execution.multilaunch.execution.ExecutionEngine
import com.intellij.execution.multilaunch.execution.ExecutionMode
import com.intellij.execution.multilaunch.execution.ExecutionSessionManager
import com.intellij.execution.runners.ProgramRunner
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.rd.util.launchBackground
import com.intellij.openapi.rd.util.lifetime
import com.intellij.openapi.wm.ToolWindowId
import com.jetbrains.rd.util.lifetime.isAlive
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class MultiLaunchProfileState(
  private val configuration: MultiLaunchConfiguration,
  private val project: Project
) : RunProfileState {
  override fun execute(executor: Executor?, runner: ProgramRunner<*>): ExecutionResult? {
    val isDumb = DumbService.isDumb(project)
    val isServiceView = RunDashboardManager.getInstance(project).isShowInDashboard(configuration)
    val factory = configuration.factory ?: throw CantRunException("factory is null")
    executor ?: throw CantRunException("executor is null")
    project.lifetime.launchBackground {
      val activity = RunConfigurationUsageTriggerCollector.trigger(project, factory, executor, configuration,
                                                                   checkRunning(), false, isDumb, isServiceView)
      ExecutionEngine.getInstance(project).execute(configuration, getExecutionMode(executor), activity)
      RunConfigurationUsageTriggerCollector.logProcessFinished(activity, RunConfigurationUsageTriggerCollector.RunConfigurationFinishType.UNKNOWN)
    }
    return null
  }

  private fun getExecutionMode(executor: Executor?): ExecutionMode =
    when {
      executor?.id == ToolWindowId.DEBUG -> ExecutionMode.Debug
      else -> ExecutionMode.Run
    }

  private fun checkRunning(): Boolean {
    val session = ExecutionSessionManager.getInstance(project).getActiveSession(configuration)
    return session != null && session.getLifetime().isAlive
  }
}