// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

/**
 * Runs [operation] in a span that is a TeamCity block. The span is current on the calling thread, so a span that
 * [operation] starts gets this span as its parent.
 */
@Internal
inline fun <T> block(name: String, crossinline operation: (Span) -> T): T = spanBuilder(name).block { operation(it) }

/** See [block]. */
@Internal
inline fun <T> SpanBuilder.block(crossinline operation: (Span) -> T): T {
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
 * Runs [operation] in a span that is a TeamCity flow. The span is current on the calling thread, so a span that
 * [operation] starts gets this span as its parent.
 *
 * See [com.intellij.platform.diagnostic.telemetry.helpers.use]
 */
@Internal
inline fun <T> SpanBuilder.use(crossinline operation: (Span) -> T): T {
  return startSpan().useWithoutActiveScope { span ->
    TeamCityBuildMessageLogger.withFlow(span) {
      span.makeCurrent().use {
        operation(span)
      }
    }
  }
}

/**
 * The twin of [use] for a body that still suspends. Only the netty client of the compilation cache needs it; the
 * span rides the coroutine context, so a span that [operation] starts on another thread gets this one as its parent.
 */
@Internal
suspend inline fun <T> SpanBuilder.useSuspending(crossinline operation: suspend CoroutineScope.(Span) -> T): T {
  return startSpan().useWithoutActiveScope { span ->
    // inner `withContext` to ensure that we report the end of the span only when all child tasks are completed,
    // the same for `withFlow` - must be out of `withContext`
    TeamCityBuildMessageLogger.withFlow(span) {
      withContext(span.asContextElement()) {
        operation(span)
      }
    }
  }
}
