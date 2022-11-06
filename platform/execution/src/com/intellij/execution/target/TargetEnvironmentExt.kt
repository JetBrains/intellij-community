// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.target

import com.intellij.execution.ExecutionException
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.blockingContext


/**
 * Same as [TargetEnvironment.createProcess] but with [Result] instead of [ExecutionException]
 */
suspend fun TargetEnvironment.createProcessWithResult(commandLine: TargetedCommandLine,
                                                      indicator: ProgressIndicator = EmptyProgressIndicator()): Result<Process> {
  try {
    return Result.success(blockingContext { createProcess(commandLine, indicator) })
  }
  catch (e: ExecutionException) {
    return Result.failure(e)
  }
}
