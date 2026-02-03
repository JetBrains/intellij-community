package com.intellij.execution.multilaunch.execution

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Internal
data class BeforeExecuteTask(
  @Nls val name: String
)