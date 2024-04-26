// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.processTools

import com.intellij.util.io.computeDetached
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runInterruptible
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CompletableFuture

// Several methods for process to get its output as kotlin result wrapped into coroutines
// Functions written top down from high level to low level


/**
 * Get process stdout as string as kotlin [Result] wrapped into [CompletableFuture]
 */
suspend fun Process.getResultStdoutStr(): Result<String> = getResultStdout().map { it.decodeToString().trim() }

/**
 * Get process stdout as [ByteArray] as [Result]
 */
suspend fun Process.getResultStdout(): Result<ByteArray> = getBareExecutionResult().let { executionResult ->
  if (executionResult.exitCode == 0) Result.success(executionResult.stdOut)
  else Result.failure(ProcessExistedNotZeroException(executionResult.stdErr, executionResult.exitCode))
}

/**
 * Get process result as [ExecutionResult].
 */
suspend fun Process.getBareExecutionResult(): ExecutionResult {
  // Streams can't be read in async manner, so they may block coroutine scope
  // To fix that, we use separate scope and cancel it when our scope is canceled.
  // This may leave blocked thread, but will cancel coroutine scope at least
  @OptIn(DelicateCoroutinesApi::class)
  return computeDetached {
    val stdOut = async { inputStream.readInterruptible() }
    val stdErr = async { errorStream.readInterruptible() }
    @Suppress("UsePlatformProcessAwaitExit")
    ExecutionResult(onExit().await().exitValue(), stdOut.await(), stdErr.await())
  }
}

/**
 * ```
 * someFuncReturnResult().mapFlat {it.someFuncThatAlsoReturnsResult()}
 * ```
 */
inline fun <T, R> Result<T>.mapFlat(mapCode: (T) -> Result<R>): Result<R> =
  map { mapCode(it) }.getOrElse { Result.failure(it) }

private suspend fun InputStream.readInterruptible(): ByteArray = runInterruptible {
  try {
    return@runInterruptible readAllBytes()
  }
  catch (io: IOException) {
    return@runInterruptible ByteArray(0)
  }
}