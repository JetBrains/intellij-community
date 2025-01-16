// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util

import fleet.tracing.runtime.CompletableSpan
import fleet.tracing.runtime.SpanStatus
import kotlinx.coroutines.CancellationException

/**
 * copy-pasted from fleet.tracing.Tracing to maintain dock api compatibility
 *
 * to be deleted the next time we are going to break compatibility
 **/
fun CompletableSpan.completeWithResult(result: Result<*>) {
  result
    .onSuccess {
      complete(SpanStatus.Success, null)
    }.onFailure { ex ->
      when (ex) {
        is CancellationException -> complete(SpanStatus.Cancelled, null)
        is InterruptedException -> complete(SpanStatus.Cancelled, null)
        else -> complete(SpanStatus.Failed(ex), null)
      }
    }
}