// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution

import com.intellij.openapi.util.Key
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
enum class ExecutorActionStatus {
  NORMAL,
  LOADING,
  RUNNING;

  companion object {
    @JvmField
    val KEY: Key<ExecutorActionStatus> = Key.create("EXECUTOR_ACTION_STATUS")
  }
}