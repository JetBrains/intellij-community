package com.intellij.execution.multilaunch.execution.executables

import com.intellij.execution.multilaunch.MultiLaunchConfiguration
import com.intellij.openapi.project.Project

interface ExecutableTemplate {
  val type: String

  fun createExecutable(project: Project, configuration: MultiLaunchConfiguration, uniqueId: String): Executable?
}
