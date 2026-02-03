package com.intellij.execution.multilaunch.statistics

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
enum class FusExecutableKind {
  RUN_CONFIGURATION,
  TASK,
  UNKNOWN,
}