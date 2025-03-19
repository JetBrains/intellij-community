package com.intellij.notebooks.visualization.ui

import kotlinx.serialization.Serializable

@Serializable
enum class ProgressStatus {
  RUNNING,
  STOPPED_OK,
  STOPPED_ERROR,
  NOT_STARTED,
  QUEUED,
  CANCELLED
}