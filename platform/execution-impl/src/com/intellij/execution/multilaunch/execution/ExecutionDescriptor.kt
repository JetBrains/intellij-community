package com.intellij.execution.multilaunch.execution

import com.jetbrains.rd.util.lifetime.Lifetime
import com.intellij.execution.multilaunch.execution.ExecutionMode
import com.intellij.execution.multilaunch.execution.conditions.Condition
import com.intellij.execution.multilaunch.execution.executables.Executable
import com.intellij.execution.multilaunch.execution.messaging.ExecutionNotifier
import com.intellij.internal.statistic.StructuredIdeActivity
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
data class ExecutionDescriptor(
  val executable: Executable,
  val condition: Condition,
  val disableDebugging: Boolean,
) {
  fun createListener(lifetime: Lifetime, executionMode: ExecutionMode, activity: StructuredIdeActivity): ExecutionNotifier {
    return condition.createExecutionListener(this, executionMode, activity, lifetime)
  }
}