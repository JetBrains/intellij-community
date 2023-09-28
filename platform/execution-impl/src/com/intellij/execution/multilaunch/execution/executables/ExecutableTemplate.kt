package com.intellij.execution.multilaunch.execution.executables

import com.intellij.execution.multilaunch.MultiLaunchConfiguration
import org.jetbrains.annotations.ApiStatus

interface ExecutableTemplate {
  val type: String

  fun createExecutable(configuration: MultiLaunchConfiguration, uniqueId: String): Executable?
}
