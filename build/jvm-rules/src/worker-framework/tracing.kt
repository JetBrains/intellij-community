// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.bazel.jvm

import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import kotlinx.coroutines.withContext
import java.util.concurrent.CancellationException
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

class OpenTelemetryContextElement(
  @JvmField val context: Context,
) : AbstractCoroutineContextElement(OpenTelemetryContextElement) {
  companion object Key : CoroutineContext.Key<OpenTelemetryContextElement>
}

suspend inline fun <T> Tracer.span(name: String, crossinline block: suspend (Span) -> T): T {
  return spanBuilder(name).use(block)
}

suspend inline fun <T> SpanBuilder.use(crossinline block: suspend (Span) -> T): T {
  val telemetryContext = requireNotNull(coroutineContext.get(OpenTelemetryContextElement)) {
    "You must set OpenTelemetryContextElement for the root coroutine scope"
  }.context
  setParent(telemetryContext)
  val span = startSpan()
  try {
    return withContext(OpenTelemetryContextElement(telemetryContext.with(span))) {
      block(span)
    }
  }
  catch (e: CancellationException) {
    throw e
  }
  catch (e: Throwable) {
    span.setStatus(StatusCode.ERROR)
    throw e
  }
  finally {
    span.end()
  }
}