// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util

import fleet.preferences.isUsingMicroSpans
import fleet.tracing.SpanInfoBuilder
import fleet.tracing.runtime.CompletableSpan
import fleet.tracing.runtime.SpanStatus
import fleet.tracing.span
import fleet.tracing.spannedScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope

private fun <T> maybeSpan(shouldSpan: Boolean, name: String, info: SpanInfoBuilder.() -> Unit = {}, body: () -> T): T =
  if (shouldSpan) {
    span(name, info, body)
  }
  else {
    body()
  }

private suspend fun <T> maybeSpannedScope(
  shouldSpan: Boolean,
  name: String,
  info: SpanInfoBuilder.() -> Unit = {},
  body: suspend CoroutineScope.() -> T,
): T =
  if (shouldSpan) {
    spannedScope(name, info, body)
  }
  else {
    coroutineScope(body)
  }


suspend fun <T> frequentSpannedScope(
  name: String,
  info: SpanInfoBuilder.() -> Unit = {},
  body: suspend CoroutineScope.() -> T,
): T =
  maybeSpannedScope(isUsingMicroSpans, name, info, body)

fun <T> frequentSpan(name: String, info: SpanInfoBuilder.() -> Unit = {}, body: () -> T) =
  maybeSpan(isUsingMicroSpans, name, info, body)

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