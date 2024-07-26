package org.jetbrains.plugins.notebooks.visualization.ui

import org.jetbrains.plugins.notebooks.visualization.r.inlays.components.progress.ProgressStatus
import java.time.ZonedDateTime

interface CellExecutionStatusView {
  fun updateExecutionStatus(executionCount: Int?, progressStatus: ProgressStatus?, startTime: ZonedDateTime?, endTime: ZonedDateTime?)
}