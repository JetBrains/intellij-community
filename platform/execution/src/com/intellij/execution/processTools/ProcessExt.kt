// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.processTools

import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CompletableFuture

// Several methods for process to get its output as kotlin result wrapped into futures
// Functions written top down from high level to low level

/**
 * Get process stdout as string or throw [ProcessExistedNotZeroException] with stderr
 */
@Throws(ProcessExistedNotZeroException::class)
fun Process.waitGetResultStdout(): String = getResultStdoutStr().get().getOrThrow()

/**
 * Get process stdout as string as kotlin [Result] wrapped into [CompletableFuture]
 */
fun Process.getResultStdoutStr(): CompletableFuture<Result<String>> =
  getResultStdout().thenApply { res -> res.map { it.decodeToString().trim() } }

/**
 * Get process stdout as [ByteArray] as [Result] wrapped into [CompletableFuture]
 */
fun Process.getResultStdout(): CompletableFuture<Result<ByteArray>> =
  getBareExecutionResult().thenApply {
    if (it.exitCode == 0) Result.success(it.stdOut)
    else Result.failure(ProcessExistedNotZeroException(it.stdErr, it.exitCode))
  }

/**
 * Get process result as [ExecutionResult] wrapped into [CompletableFuture]
 */
fun Process.getBareExecutionResult(): CompletableFuture<ExecutionResult> {
  val stderrFuture = errorStream.future
  val stdoutFuture = inputStream.future
  val processFuture = CompletableFuture.supplyAsync {
    waitFor()
  }
  return CompletableFuture.allOf(stderrFuture, stdoutFuture, processFuture).thenApply {
    ExecutionResult(processFuture.get(), stdoutFuture.get(), stderrFuture.get())
  }
}

/**
 * Fetch stream data in async manner and return [ByteArray]
 */
private val InputStream.future: CompletableFuture<ByteArray>
  get() = CompletableFuture.supplyAsync {
    try {
      readAllBytes()
    }
    catch (e: IOException) {
      ByteArray(0)
    }
  }
