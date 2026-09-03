// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.telemetry

import com.intellij.platform.diagnostic.telemetry.helpers.useWithoutActiveScope
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.extension.kotlin.asContextElement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.intellij.build.logging.TeamCityBuildMessageLogger
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

suspend inline fun <T> block(
  name: String,
  crossinline operation: suspend CoroutineScope.(Span) -> T,
): T {
  return spanBuilder(name).block { operation(it) }
}

// inline like `use`, because `withBlock` takes a plain lambda and only an inlined one may suspend
suspend inline fun <T> SpanBuilder.block(
  coroutineContext: CoroutineContext = EmptyCoroutineContext,
  crossinline operation: suspend CoroutineScope.(Span) -> T,
): T {
  TraceManager.scheduleExportPendingSpans()
  return startSpan().useWithoutActiveScope { span ->
    // see `use` below why `withContext` must be inner
    TeamCityBuildMessageLogger.withBlock(span) {
      withContext(span.asContextElement() + coroutineContext) {
        operation(span)
      }
    }
  }
}

/**
 * The non-suspend twin of [block]. It makes the span current on the calling thread, so a span that [operation] starts
 * gets this span as its parent.
 */
@Internal
inline fun <T> blockingBlock(name: String, crossinline operation: (Span) -> T): T = spanBuilder(name).blockingBlock { operation(it) }

/** See [blockingBlock]. */
@Internal
inline fun <T> SpanBuilder.blockingBlock(crossinline operation: (Span) -> T): T {
  TraceManager.scheduleExportPendingSpans()
  return startSpan().useWithoutActiveScope { span ->
    TeamCityBuildMessageLogger.withBlock(span) {
      span.makeCurrent().use {
        operation(span)
      }
    }
  }
}

/**
 * See [com.intellij.platform.diagnostic.telemetry.helpers.use]
 */
@Internal
suspend inline fun <T> SpanBuilder.use(
  context: CoroutineContext = EmptyCoroutineContext,
  crossinline operation: suspend CoroutineScope.(Span) -> T,
): T {
  return startSpan().useWithoutActiveScope { span ->
    // inner `withContext` to ensure that we report the end of the span only when all child tasks are completed,
    // the same for `withFlow` - must be out of `withContext`
    TeamCityBuildMessageLogger.withFlow(span) {
      withContext(span.asContextElement() + context) {
        operation(span)
      }
    }
  }
}

/**
 * The non-suspend twin of [use]. It makes the span current on the calling thread, so a span that [operation] starts
 * gets this span as its parent.
 *
 * See [com.intellij.platform.diagnostic.telemetry.helpers.use]
 */
@Internal
inline fun <T> SpanBuilder.blockingUse(crossinline operation: (Span) -> T): T {
  return startSpan().useWithoutActiveScope { span ->
    TeamCityBuildMessageLogger.withFlow(span) {
      span.makeCurrent().use {
        operation(span)
      }
    }
  }
}