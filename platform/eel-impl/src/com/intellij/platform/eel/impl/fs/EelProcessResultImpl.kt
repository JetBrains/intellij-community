// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.fs

import com.intellij.platform.eel.EelExecApi
import com.intellij.platform.eel.EelProcess
import com.intellij.platform.eel.EelResult
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object EelProcessResultImpl {
  private data class Ok<P : EelProcess>(override val value: P) : EelResult.Ok<P>
  private data class Error(override val error: EelExecApi.ExecuteProcessError) : EelResult.Error<EelExecApi.ExecuteProcessError>
  private data class ExecuteProcessError(override val errno: Int, override val message: String) : EelExecApi.ExecuteProcessError

  fun <P : EelProcess> createOkResult(process: P): EelResult.Ok<P> = Ok(process)
  fun createErrorResult(errno: Int, message: String): EelResult.Error<EelExecApi.ExecuteProcessError> = Error(ExecuteProcessError(errno, message))
}