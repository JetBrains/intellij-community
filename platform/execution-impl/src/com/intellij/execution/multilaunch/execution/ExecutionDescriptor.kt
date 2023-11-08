package com.intellij.execution.multilaunch.execution

import com.jetbrains.rd.util.lifetime.Lifetime
import com.intellij.execution.multilaunch.execution.ExecutionMode
import com.intellij.execution.multilaunch.execution.conditions.Condition
import com.intellij.execution.multilaunch.execution.executables.Executable
import com.intellij.execution.multilaunch.execution.messaging.ExecutionNotifier

data class ExecutionDescriptor(
  val executable: Executable,
  val condition: Condition,
  val disableDebugging: Boolean,
) {
  fun createListener(lifetime: Lifetime, executionMode: ExecutionMode): ExecutionNotifier {
    return condition.createExecutionListener(this, executionMode, lifetime)
  }
}