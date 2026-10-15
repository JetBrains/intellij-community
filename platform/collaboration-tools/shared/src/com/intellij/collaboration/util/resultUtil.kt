// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.util

import com.intellij.openapi.diagnostic.rethrowControlFlowException
import java.util.concurrent.CancellationException

object ResultUtil {
  /**
   * Runs the [block] catching user exceptions (not [Error], not [com.intellij.openapi.diagnostic.ControlFlowException])
   */
  inline fun <R> runCatchingUser(block: () -> R): Result<R> =
    try {
      Result.success(block())
    }
    catch (e: Exception) {
      rethrowControlFlowException(e)
      Result.failure(e)
    }

  /**
   * Allows processing the error before re-throwing it
   * Will silently re-throw cancellation and JVM errors
   */
  inline fun <T> Result<T>.processErrorAndGet(handler: (e: Throwable) -> Unit): T =
    onFailure {
      if (it !is CancellationException && it !is Error) handler(it)
    }.getOrThrow()
}