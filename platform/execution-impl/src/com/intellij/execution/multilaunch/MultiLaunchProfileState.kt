package com.intellij.execution.multilaunch

import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.runners.ProgramRunner
import com.intellij.openapi.project.Project
import com.intellij.openapi.rd.util.launchBackground
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.execution.multilaunch.execution.ExecutionMode
import com.intellij.execution.multilaunch.execution.ExecutionEngine
import com.intellij.openapi.rd.util.lifetime

class MultiLaunchProfileState(
  private val configuration: MultiLaunchConfiguration,
  private val project: Project
) : RunProfileState {
  override fun execute(executor: Executor?, runner: ProgramRunner<*>): ExecutionResult? {
    project.lifetime.launchBackground {
      ExecutionEngine.getInstance(project).execute(configuration, getExecutionMode(executor))
    }
    return null
  }

  private fun getExecutionMode(executor: Executor?): ExecutionMode =
    when {
      executor?.id == ToolWindowId.DEBUG -> ExecutionMode.Debug
      else -> ExecutionMode.Run
    }
}