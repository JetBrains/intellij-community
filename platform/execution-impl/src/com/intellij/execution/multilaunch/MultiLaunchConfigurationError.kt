package com.intellij.execution.multilaunch

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.openapi.util.NlsContexts.DialogMessage
import com.intellij.execution.multilaunch.execution.executables.Executable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class MultiLaunchConfigurationError(@DialogMessage message: String) : RuntimeConfigurationError(message) {
  constructor(executable: Executable, @DialogMessage message: String) : this(
    ExecutionBundle.message("run.configurations.multilaunch.error.template.executable.error", executable.name, message))
  constructor(row: Int, @DialogMessage message: String) : this(ExecutionBundle.message("run.configurations.multilaunch.error.template.row.error", row, message))
  constructor(executable: Executable, row: Int, @DialogMessage message: String) : this(ExecutionBundle.message("run.configurations.multilaunch.error.template.executable.row.error", executable.name, row, message))
}