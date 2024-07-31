// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.telemetry

import com.intellij.platform.diagnostic.telemetry.helpers.use
import com.intellij.platform.diagnostic.telemetry.helpers.useWithScope
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.intellij.build.logging.TeamCityBuildMessageLogger
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Executes [operation] in a new [Span] optionally calling [TeamCityBuildMessageLogger.withFlow]
 */
@Internal
suspend inline fun <T> SpanBuilder.useWithScope(
  context: CoroutineContext = EmptyCoroutineContext,
  crossinline operation: suspend CoroutineScope.(Span) -> T,
): T {
  return useWithScope(context) { span ->
    TeamCityBuildMessageLogger.withFlow(span) {
      coroutineScope {
        operation(span)
      }
    }
  }
}

/**
 * See [com.intellij.platform.diagnostic.telemetry.helpers.use]
 */
@Internal
inline fun <T> SpanBuilder.use(operation: (Span) -> T): T {
  return use { span ->
    TeamCityBuildMessageLogger.withFlow(span) {
      operation(span)
    }
  }
}

/**
 * See [com.intellij.platform.diagnostic.telemetry.helpers.use]
 */
@Internal
inline fun <T> Span.use(operation: (Span) -> T): T {
  return use { span ->
    TeamCityBuildMessageLogger.withFlow(span) {
      operation(span)
    }
  }
}
