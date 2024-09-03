package com.intellij.notebooks.visualization.ui

enum class ProgressStatus {
  RUNNING,
  STOPPED_OK,
  STOPPED_ERROR,
  NOT_STARTED,
  QUEUED,
  CANCELLED
}