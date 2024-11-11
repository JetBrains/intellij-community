// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.provider

import com.intellij.platform.eel.EelExecApi
import com.intellij.platform.eel.EelProcess
import com.intellij.platform.eel.EelResult
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
object EelProcessResultImpl {
  private data class Ok(override val value: EelProcess) : EelResult.Ok<EelProcess>
  private data class Error(override val error: EelExecApi.ExecuteProcessError) : EelResult.Error<EelExecApi.ExecuteProcessError>
  private data class ExecuteProcessError(override val errno: Int, override val message: String) : EelExecApi.ExecuteProcessError

  fun createOkResult(process: EelProcess): EelResult.Ok<EelProcess> = Ok(process)
  fun createErrorResult(errno: Int, message: String): EelResult.Error<EelExecApi.ExecuteProcessError> = Error(ExecuteProcessError(errno, message))
}