// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util

import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive

fun interface CancellationToken {
  fun checkCancelled()

  companion object NonCancellable : CancellationToken {
    override fun checkCancelled() {}
  }
}

fun Job.cancellationToken(): CancellationToken {
  var counter = 0
  return CancellationToken {
    if ((counter++) % 100 == 0) {
      ensureActive()
    }
  }
}