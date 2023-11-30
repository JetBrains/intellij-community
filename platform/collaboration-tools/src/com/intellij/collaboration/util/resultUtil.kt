// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.util

import java.util.concurrent.CancellationException

object ResultUtil {
  /**
   * Runs the [block] catching user exceptions (not [Error], not [CancellationException])
   */
  inline fun <R> runCatchingUser(block: () -> R): Result<R> =
    try {
      Result.success(block())
    }
    catch (ce: CancellationException) {
      throw ce
    }
    catch (e: Exception) {
      Result.failure(e)
    }
}