// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.provider.utils

import com.intellij.platform.eel.EelProcess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.io.ByteArrayOutputStream
import kotlin.coroutines.CoroutineContext

/**
 * To simplify [EelProcessExecutionResult] delegation
 */
@ApiStatus.Experimental
interface EelProcessExecutionResultInfo {
  val exitCode: Int
  val stdout: ByteArray
  val stderr: ByteArray
}

@get:ApiStatus.Experimental
val EelProcessExecutionResultInfo.stdoutString: String get() = String(stdout)

@get:ApiStatus.Experimental
val EelProcessExecutionResultInfo.stderrString: String get() = String(stderr)

@ApiStatus.Experimental
class EelProcessExecutionResult(
  override val exitCode: Int,
  override val stdout: ByteArray,
  override val stderr: ByteArray,
) : EelProcessExecutionResultInfo {
  override fun toString(): String = "EelProcessExecutionResult(exitCode=$exitCode, stdout=$stdoutString}, stderr=$stderrString)"
}

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
@ApiStatus.Experimental
suspend fun EelProcess.awaitProcessResult(): EelProcessExecutionResult {
  return computeDetached {
    ByteArrayOutputStream().use { out ->
      ByteArrayOutputStream().use { err ->
        coroutineScope {
          launch {
            copy(stdout, out.asEelChannel()) // TODO: process errors
          }

          launch {
            copy(stderr, err.asEelChannel()) // TODO: process errors
          }
        }

        EelProcessExecutionResult(exitCode.await(), out.toByteArray(), err.toByteArray())
      }
    }
  }
}

@OptIn(ExperimentalCoroutinesApi::class)
internal val unlimitedDispatcher = Dispatchers.IO.limitedParallelism(parallelism = Int.MAX_VALUE)

// TODO: Move com.intellij.util.io.ProcessKt.computeDetached to intellij.platform.util.coroutines module, then remove this duplicate
@DelicateCoroutinesApi
internal suspend fun <T> computeDetached(dispatcher: CoroutineContext = unlimitedDispatcher, action: suspend CoroutineScope.() -> T): T {
  val deferred = GlobalScope.async(dispatcher, block = action)
  try {
    return deferred.await()
  }
  catch (ce: CancellationException) {
    deferred.cancel(ce)
    throw ce
  }
}
