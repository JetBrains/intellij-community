package com.intellij.execution.multilaunch.execution

import com.intellij.execution.multilaunch.execution.executables.Executable
import kotlinx.coroutines.CompletableDeferred

internal data class ExecutableContext(
  var executionResult: CompletableDeferred<Unit>,
  var executable: Executable,
  var status: ExecutionStatus
)