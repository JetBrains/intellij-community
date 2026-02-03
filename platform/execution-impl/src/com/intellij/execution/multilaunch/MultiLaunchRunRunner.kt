package com.intellij.execution.multilaunch

import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class MultiLaunchRunRunner : ProgramRunner<RunnerSettings> {
  override fun getRunnerId() = "MultiLaunchRunRunner"

  override fun canRun(executorId: String, profile: RunProfile) =
    executorId == DefaultRunExecutor.EXECUTOR_ID && profile is MultiLaunchConfiguration

  override fun execute(environment: ExecutionEnvironment) {
    environment.state?.execute(DefaultRunExecutor.getRunExecutorInstance(), this)
  }
}