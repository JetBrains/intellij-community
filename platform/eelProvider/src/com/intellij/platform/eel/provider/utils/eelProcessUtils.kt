// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.provider.utils

import com.intellij.execution.process.ProcessOutput
import com.intellij.platform.eel.*
import com.intellij.platform.eel.fs.getPath
import com.intellij.platform.eel.path.EelPath
import com.intellij.util.io.computeDetached
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

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
            stdout.consumeEach(out::write)
          }

          launch {
            stderr.consumeEach(err::write)
          }
        }

        ProcessOutput(String(out.toByteArray()), String(err.toByteArray()), exitCode.await(), false, false)
      }
    }
  }
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
suspend fun EelApi.where(exe: String): EelPath.Absolute? {
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
    return fs.getPath(result.stdout)
  }
}