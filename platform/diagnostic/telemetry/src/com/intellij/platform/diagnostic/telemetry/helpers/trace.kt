// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry.helpers

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.ThrowableNotNullFunction
import com.intellij.platform.diagnostic.telemetry.IJTracer
import com.intellij.util.ThrowableConsumer
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import io.opentelemetry.extension.kotlin.asContextElement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import java.util.concurrent.CancellationException
import java.util.function.Consumer
import kotlin.coroutines.CoroutineContext

inline fun <T> SpanBuilder.useWithScope(operation: (Span) -> T): T {
  val span = startSpan()
  return span.makeCurrent().use {
    span.use(operation)
  }
}

suspend inline fun <T> SpanBuilder.useWithScope(context: CoroutineContext, crossinline operation: suspend CoroutineScope.(Span) -> T): T {
  val span = startSpan()
  return withContext(Context.current().with(span).asContextElement() + context) {
    span.use {
      operation(span)
    }
  }
}

suspend inline fun <T> SpanBuilder.useWithScope2(crossinline operation: suspend (Span) -> T): T {
  val span = startSpan()
  return withContext(Context.current().with(span).asContextElement()) {
    span.use {
      operation(span)
    }
  }
}

fun runWithSpanSimple(tracer: IJTracer, spanName: String, operation: Runnable) {
  runWithSpan(tracer, spanName) { _ -> operation.run() }
}

fun <T> computeWithSpanAttribute(tracer: IJTracer,
                                 spanName: String,
                                 attributeName: String,
                                 attributeValue: (T) -> String,
                                 operation: () -> T): T {
  return computeWithSpan(tracer, spanName) { span ->
    val result = operation.invoke()
    span.setAttribute(attributeName, attributeValue.invoke(result))
    return@computeWithSpan result
  }
}

fun <T> computeWithSpanAttributes(tracer: IJTracer,
                                  spanName: String,
                                  attributesGenerator: (T) -> Map<String, String>,
                                  operation: () -> T): T {
  return computeWithSpan(tracer, spanName) { span ->
    val result = operation.invoke()
    attributesGenerator.invoke(result).forEach { (attributeName, attributeValue) ->
      span.setAttribute(attributeName, attributeValue)
    }
    return@computeWithSpan result
  }
}

inline fun <T> computeWithSpan(tracer: Tracer, spanName: String, operation: (Span) -> T): T {
  return tracer.spanBuilder(spanName).useWithScope(operation)
}

inline fun runWithSpan(tracer: Tracer, spanName: String, operation: (Span) -> Unit) {
  tracer.spanBuilder(spanName).useWithScope(operation)
}

internal fun <T> computeWithSpanIgnoreThrows(tracer: Tracer,
                                             spanName: String,
                                             operation: ThrowableNotNullFunction<Span, T, out Throwable>): T {
  return tracer.spanBuilder(spanName).useWithScope(operation::`fun`)
}

internal fun runWithSpanIgnoreThrows(tracer: Tracer, spanName: String, operation: ThrowableConsumer<Span, out Throwable>) {
  tracer.spanBuilder(spanName).useWithScope(operation::consume)
}

fun runWithSpan(tracer: Tracer, spanName: String, operation: Consumer<Span>) {
  tracer.spanBuilder(spanName).useWithScope(operation::accept)
}

inline fun <T> SpanBuilder.use(operation: (Span) -> T): T {
  return startSpan().use(operation)
}

inline fun Span.useWithScope(operation: () -> Unit) {
  makeCurrent().use {
    use { operation.invoke() }
  }
}

fun Span.runSpanWithScope(operation: Runnable) {
  makeCurrent().use {
    use { operation.run() }
  }
}

inline fun <T> Span.use(operation: (Span) -> T): T {
  try {
    return operation(this)
  }
  catch (e: CancellationException) {
    throw e
  }
  catch (e: ProcessCanceledException) {
    throw e
  }
  catch (e: Throwable) {
    recordException(e)
    setStatus(StatusCode.ERROR)
    throw e
  }
  finally {
    end()
  }
}