package com.intellij.execution.multilaunch.servicesView

import com.intellij.execution.multilaunch.execution.ExecutionDescriptor
import com.intellij.execution.multilaunch.execution.ExecutableContext

data class ExecutableServiceState(
  val descriptor: ExecutionDescriptor,
  val context: ExecutableContext
)