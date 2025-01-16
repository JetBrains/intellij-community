// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.utils

import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.platform.eel.*
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.ResultErrImpl
import com.intellij.platform.eel.provider.ResultOkImpl
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.utils.asEelChannel
import com.intellij.platform.eel.provider.utils.copy
import com.intellij.util.io.computeDetached
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import kotlin.io.path.pathString
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/**
 * Function that awaits the completion of an [EelProcess] and retrieves its execution result,
 * including the exit code, standard output, and standard error streams.
 *
 * @example
 * ```kotlin
 * val process = eelApi.exec.executeProcess("ls", "-la").getOrThrow()
 * val result = process.awaitProcessResult()
 * println("Exit code: ${result.exitCode}")
 * println("Standard Output: ${result.stdout}")
 * println("Standard Error: ${result.stderr}")
 * ```
 *
 * @see EelProcess
 * @see ProcessOutput
 */
@OptIn(DelicateCoroutinesApi::class)
suspend fun EelProcess.awaitProcessResult(): ProcessOutput {
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

        ProcessOutput(String(out.toByteArray()), String(err.toByteArray()), exitCode.await(), false, false)
      }
    }
  }
}

fun EelApi.whereBlocking(exe: String): EelPath? {
  return runBlockingMaybeCancellable { where(exe) }
}

/**
 * Function that attempts to locate the full path of the specified executable in the system's PATH.
 *
 * This function uses platform-specific commands to search for the executable:
 * - On POSIX systems, it uses the `which` command.
 * - On Windows systems, it uses `where.exe`.
 *
 * @param exe The name of the executable to search for.
 * @return The full path to the executable if found; `null` if the executable is not found or an error occurs.
 * @throws IllegalArgumentException If the operating system is unsupported.
 *
 * @example
 * ```kotlin
 * val path = eelApi.where("git")
 * if (path != null) {
 *     println("Git is located at: $path")
 * } else {
 *     println("Git executable not found.")
 * }
 * ```
 */
suspend fun EelApi.where(exe: String): EelPath? {
  val tool = when (this) {
    is EelPosixApi -> "which"
    is EelWindowsApi -> "where.exe"
    else -> throw IllegalArgumentException("Unsupported OS: $this")
  }

  val result = exec.executeProcess(tool, exe).getOrThrow().awaitProcessResult()

  if (result.exitCode != 0) {
    // TODO: log error?/throw Exception?
    return null
  }
  else {
    return EelPath.parse(result.stdout.trim(), descriptor)
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
@Internal
@ApiStatus.Experimental
suspend fun Path.exec(vararg args: String, timeout: Duration = Int.MAX_VALUE.days): EelResult<ProcessOutput, EelExecApi.ExecuteProcessError?> {

  val process = getEelDescriptor().upgrade().exec.executeProcess(pathString, *args).getOr { return it }
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