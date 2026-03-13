package com.intellij.execution.multilaunch.servicesView

import com.intellij.execution.multilaunch.execution.ExecutableContext
import com.intellij.execution.multilaunch.execution.ExecutionDescriptor

internal data class ExecutableServiceState(
  val descriptor: ExecutionDescriptor,
  val context: ExecutableContext
)