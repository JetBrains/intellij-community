package com.intellij.execution.multilaunch.execution

internal sealed interface ExecutionStatus {
  object NotStarted : ExecutionStatus
  object Waiting : ExecutionStatus
  object Started : ExecutionStatus
  object Finished : ExecutionStatus
  object Canceled : ExecutionStatus
  data class Failed(val reason: Throwable?) : ExecutionStatus
}

internal fun ExecutionStatus.isRunning(): Boolean = when (this) {
  is ExecutionStatus.Waiting -> true
  is ExecutionStatus.Started -> true
  else -> false
}

internal fun ExecutionStatus.isDone(): Boolean = when (this) {
  is ExecutionStatus.Finished -> true
  is ExecutionStatus.Canceled -> true
  is ExecutionStatus.Failed -> true
  else -> false
}