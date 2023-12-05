package com.intellij.execution.multilaunch.execution

import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.multilaunch.MultiLaunchConfiguration
import com.intellij.execution.multilaunch.execution.executables.Executable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.jetbrains.rd.util.reactive.Property
import com.jetbrains.rd.util.reactive.ViewableMap

@Service(Service.Level.PROJECT)
class ExecutionModel {
  companion object {
    fun getInstance(project: Project) = project.service<ExecutionModel>()
  }

  val configurations = ViewableMap<MultiLaunchConfiguration, MultiLaunchExecutionModel>()
}

class MultiLaunchExecutionModel(val settings: RunnerAndConfigurationSettings, val configuration: MultiLaunchConfiguration) {
  val executables = ViewableMap<Executable, ExecutableExecutionModel>()
  val isDone = Property(false)

  fun isDone(): Boolean {
    return executables.values.all { it.status.value.isDone() }
  }

  fun isRunning(): Boolean {
    return executables.values.all { it.status.value.isRunning() }
  }
}

class ExecutableExecutionModel(val descriptor: ExecutionDescriptor) {
  val status = Property<ExecutionStatus>(ExecutionStatus.NotStarted)
}