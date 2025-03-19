package com.intellij.notebooks.visualization.ui

import java.time.ZonedDateTime

interface CellExecutionStatusView {
  fun updateExecutionStatus(executionCount: Int?, progressStatus: ProgressStatus?, startTime: ZonedDateTime?, endTime: ZonedDateTime?)
}