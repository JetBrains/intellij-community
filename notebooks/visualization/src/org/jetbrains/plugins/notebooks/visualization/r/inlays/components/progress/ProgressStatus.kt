package org.jetbrains.plugins.notebooks.visualization.r.inlays.components.progress


enum class ProgressStatus {
  RUNNING,
  STOPPED_OK,
  STOPPED_ERROR,
  NOT_STARTED,
  QUEUED,
  CANCELLED
}