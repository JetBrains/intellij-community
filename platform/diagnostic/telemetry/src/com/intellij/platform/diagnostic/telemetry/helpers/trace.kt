// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry.helpers

import com.intellij.openapi.diagnostic.rethrowControlFlowException
import com.intellij.platform.diagnostic.telemetry.IJTracer
import com.intellij.util.ThrowableConsumer
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.context.Context
import io.opentelemetry.extension.kotlin.asContextElement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Internal
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Starts a new span and adds it to the current scope for the [operation].
 * That way the spans created inside the [operation] will be nested to the created span.
 *
 * See [span concept](https://opentelemetry.io/docs/concepts/signals/traces/#spans) for more details on span nesting.
 */
@Internal
inline fun <T> SpanBuilder.use(operation: (Span) -> T): T {
  return startSpan().useWithoutActiveScope { span ->
    span.makeCurrent().use {
      operation(span)
    }
  }
}

/**
 * Starts a new span and adds it to the current scope.
 * That way the spans created inside the [operation] will be nested to the created span.
 *
 * See [span concept](https://opentelemetry.io/docs/concepts/signals/traces/#spans) for more details on span nesting.
 */
@Internal
inline fun <T> Span.use(operation: (Span) -> T): T {
  return useWithoutActiveScope {
    makeCurrent().use {
      operation(this)
    }
  }
}

@Internal
suspend inline fun <T> SpanBuilder.useWithScope(
  context: CoroutineContext = EmptyCoroutineContext,
  crossinline operation: suspend CoroutineScope.(Span) -> T,
): T {
  return startSpan().useWithoutActiveScope { span ->
    // inner withContext to ensure that we report the end of the span only when all child tasks are completed
    withContext(context + Context.current().with(span).asContextElement()) {
      operation(span)
    }
  }
}

@Internal
internal fun runWithSpanIgnoreThrows(spanBuilder: SpanBuilder, operation: ThrowableConsumer<Span, out Throwable>) {
  spanBuilder.use(operation::consume)
}

/**
 * Do not use it.
 * Only for implementation.
 *
 * Does not activate the span scope, so **new spans created inside [operation] will not be linked to [this] span**.
 * [Span] supplied as an argument to [operation] may not be the [Span.current].
 * No overhead of [Context.makeCurrent] is incurred.
 *
 * Consider using [use] to also activate the scope.
 */
@Internal
inline fun <T> Span.useWithoutActiveScope(operation: (Span) -> T): T {
  try {
    return operation(this)
  }
  catch (e: Throwable) {
    rethrowControlFlowException(e)
    setStatus(StatusCode.ERROR)
    throw e
  }
  finally {
    end()
  }
}

/**
 * Runs [operation] inside a span named [spanName], and records the operation's start immediately as a
 * separate zero-duration `"<name>: started"` span.
 *
 * A span reaches the exporters only when it ends, so a span that outlives the
 * [io.opentelemetry.sdk.trace.SpanProcessor] — a loop that ends only at shutdown, for example — is dropped,
 * and its start is never logged. The extra span records that start. No [Span] is passed to [operation];
 * events on the real span would be dropped too.
 *
 * **Not the canonical form — prefer a metric.**
 *
 * OpenTelemetry has no export-at-start hook (`onStart` only enriches a span synchronously), so a span cannot
 * report an operation that never ends in time. The canonical signal is a metric — a counter of starts, or an
 * up-down counter of active operations. The span stays only because nothing consumes this marker as a span
 * today.
 *
 * References:
 * - [Trace SDK: spans export on end, not on start](https://opentelemetry.io/docs/specs/otel/trace/sdk/#span-processor)
 * - [Metrics API: Counter and UpDownCounter](https://opentelemetry.io/docs/specs/otel/metrics/api/#updowncounter)
 */
@Internal
inline fun <T> IJTracer.spanWithExplicitStart(spanName: String, operation: () -> T): T {
  this.spanBuilder("$spanName: started").startSpan().end()
  return this.spanBuilder(spanName).use { operation() }
}
