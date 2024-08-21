package org.jetbrains.plugins.notebooks.visualization.execution

import org.jetbrains.plugins.notebooks.visualization.ui.ProgressStatus
import java.time.ZonedDateTime

sealed interface ExecutionEvent {

  class ExecutionStarted(val status: ProgressStatus?, val startTime: ZonedDateTime) : ExecutionEvent
  class ExecutionSubmitted(val status: ProgressStatus?) : ExecutionEvent
  class ExecutionStopped(val status: ProgressStatus?, val endTime: ZonedDateTime, val executionCount: Int?) : ExecutionEvent
  class ExecutionReset(val status: ProgressStatus?) : ExecutionEvent

}