// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(IntellijInternalApi::class)

package com.intellij.platform.eel.provider.utils

import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.platform.eel.*
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.ResultErrImpl
import com.intellij.platform.eel.provider.ResultOkImpl
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.util.io.computeDetached
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.annotations.ApiStatus
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import kotlin.io.path.pathString
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

val EelProcessExecutionResult.stdoutString: String get() = String(stdout)
val EelProcessExecutionResult.stderrString: String get() = String(stderr)

class EelProcessExecutionResult(val exitCode: Int, val stdout: ByteArray, val stderr: ByteArray)

/**
 * Function that awaits the completion of an [EelProcess] and retrieves its execution result,
 * including the exit code, standard output, and standard error streams.
 *
 * @example
 * ```kotlin
 * val process = eelApi.exec.executeProcess("ls", "-la").getOrThrow()
 * val result = process.awaitProcessResult()
 * println("Exit code: ${result.exitCode}")
 * println("Standard Output: ${String(result.stdout)}")
 * println("Standard Error: ${String(result.stderr)}")
 * ```
 *
 * @see EelProcess
 */
@OptIn(DelicateCoroutinesApi::class)
suspend fun EelProcess.awaitProcessResult(): EelProcessExecutionResult {
  return computeDetached {
    ByteArrayOutputStream().use { out ->
      ByteArrayOutputStream().use { err ->
        coroutineScope {
          launch {
            copy(stdout, out.asEelChannel()).getOrThrow() // TODO: process errors
          }

          launch {
            copy(stderr, err.asEelChannel()).getOrThrow() // TODO: process errors
          }
        }

        EelProcessExecutionResult(exitCode.await(), out.toByteArray(), err.toByteArray())
      }
    }
  }
}

/**
 * Given [this] is a binary, executes it with [args] and returns either [EelExecApi.ExecuteProcessError] (couldn't execute) or
 * [ProcessOutput] as a result of the execution.
 * If [timeout] elapsed then return value is an error with `null`.
 * ```kotlin
 * withTimeout(10.seconds) {python.exec("-v")}.getOr{return it}
 * ```
 */
@ApiStatus.Internal
@ApiStatus.Experimental
suspend fun Path.exec(vararg args: String, timeout: Duration = Int.MAX_VALUE.days): EelResult<EelProcessExecutionResult, EelExecApi.ExecuteProcessError?> {

  val process = getEelDescriptor().upgrade().exec.execute(pathString, *args).eelIt().getOr { return it }
  val output = withTimeoutOrNull(timeout) {
    process.awaitProcessResult()
  }
  return if (output != null) {
    ResultOkImpl(output)
  }
  else {
    process.kill()
    ResultErrImpl(null)
  }
}