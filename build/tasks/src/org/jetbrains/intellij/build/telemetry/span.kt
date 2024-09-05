// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.telemetry

import com.intellij.platform.diagnostic.telemetry.helpers.useWithoutActiveScope
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.context.Context
import io.opentelemetry.extension.kotlin.asContextElement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.intellij.build.logging.TeamCityBuildMessageLogger
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

suspend fun <T> block(
  name: String,
  operation: suspend CoroutineScope.(Span) -> T,
): T {
  return spanBuilder(name).block(operation = operation)
}

suspend fun <T> SpanBuilder.block(
  coroutineContext: CoroutineContext = EmptyCoroutineContext,
  operation: suspend CoroutineScope.(Span) -> T,
): T {
  TraceManager.scheduleExportPendingSpans()
  return startSpan().useWithoutActiveScope { span ->
    // see `use` below why `withContext` must be inner
    TeamCityBuildMessageLogger.withBlock(span) {
      withContext(Context.current().with(span).asContextElement() + coroutineContext) {
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
      withContext(Context.current().with(span).asContextElement() + context) {
        operation(span)
      }
    }
  }
}