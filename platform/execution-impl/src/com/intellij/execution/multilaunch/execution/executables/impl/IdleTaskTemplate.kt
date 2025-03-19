package com.intellij.execution.multilaunch.execution.executables.impl

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.util.ui.EmptyIcon
import com.jetbrains.rd.util.lifetime.Lifetime
import com.intellij.execution.multilaunch.MultiLaunchConfiguration
import com.intellij.execution.multilaunch.execution.ExecutionMode
import com.intellij.execution.multilaunch.execution.executables.Executable
import com.intellij.execution.multilaunch.execution.executables.TaskExecutableTemplate
import com.intellij.execution.multilaunch.state.ExecutableSnapshot
import com.intellij.internal.statistic.StructuredIdeActivity
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class IdleTaskTemplate : TaskExecutableTemplate() {
  override val type = "idle"

  override fun createExecutable(project: Project,
                                configuration: MultiLaunchConfiguration,
                                uniqueId: String): Executable = IdleExecutable(uniqueId)

  inner class IdleExecutable(uniqueId: String) : Executable(uniqueId, ExecutionBundle.message("run.configurations.multilaunch.executable.idle"), EmptyIcon.ICON_16, this@IdleTaskTemplate) {
    override suspend fun execute(mode: ExecutionMode, activity: StructuredIdeActivity, lifetime: Lifetime): RunContentDescriptor? {
      return null
    }

    override fun saveAttributes(snapshot: ExecutableSnapshot) {}
    override fun loadAttributes(snapshot: ExecutableSnapshot) {}
  }
}