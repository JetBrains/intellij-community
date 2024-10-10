// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.tracing

import fleet.tracing.runtime.CompletableSpan
import fleet.tracing.runtime.Span
import fleet.tracing.runtime.SpanStatus
import fleet.tracing.runtime.currentSpan
import fleet.tracing.runtime.tl.currentSpanThreadLocal
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

private inline fun <T> withSpan(span: CompletableSpan, body: (Span) -> T): T =
  try {
    body(span).also {
      span.complete(SpanStatus.Success, null)
    }
  }
  catch (x: CancellationException) {
    span.complete(SpanStatus.Cancelled, null)
    throw x
  }
  catch (x: InterruptedException) {
    span.complete(SpanStatus.Cancelled, null)
    throw x
  }
  catch (x: Throwable) {
    span.complete(SpanStatus.Failed(x), null)
    throw x
  }

fun <T> withCurrentSpan(span: Span, body: () -> T): T =
  currentSpanThreadLocal.get().let { oldSpan ->
    try {
      currentSpanThreadLocal.set(span)
      body()
    }
    finally {
      currentSpanThreadLocal.set(oldSpan)
    }
  }

fun <T> span(name: String, info: SpanInfoBuilder.() -> Unit = {}, body: () -> T): T =
  currentSpan.let { span ->
    when (span) {
      is Span.Noop -> body()
      else -> withSpan(span.startChild(spanInfo(name, span.job, false, info))) { child ->
        withCurrentSpan(child, body)
      }
    }
  }

fun Span.asContextElement(): CoroutineContext.Element =
  currentSpanThreadLocal.asContextElement(this)

suspend fun <T> spannedScope(name: String, info: SpanInfoBuilder.() -> Unit = {}, body: suspend CoroutineScope.() -> T): T =
  spannedScope(currentSpan.startChild(spanInfo(name, coroutineContext.job, true, info)), body)

suspend fun <T> spannedScope(
  span: CompletableSpan,
  body: suspend CoroutineScope.() -> T,
): T =
  withSpan(span) {
    withContext(span.asContextElement(), body)
  }
