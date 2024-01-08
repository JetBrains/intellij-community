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
import kotlin.coroutines.EmptyCoroutineContext

inline fun <T> SpanBuilder.useWithScopeBlocking(operation: (Span) -> T): T {
  val span = startSpan()
  return span.makeCurrent().use {
    span.use(operation)
  }
}

suspend inline fun <T> SpanBuilder.useWithScope(context: CoroutineContext = EmptyCoroutineContext,
                                                crossinline operation: suspend CoroutineScope.(Span) -> T): T {
  val span = startSpan()
  return withContext(Context.current().with(span).asContextElement() + context) {
    try {
      operation(span)
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Throwable) {
      span.recordException(e)
      span.setStatus(StatusCode.ERROR)
      throw e
    }
    finally {
      span.end()
    }
  }
}

fun <T> computeWithSpanAttribute(tracer: IJTracer,
                                 spanName: String,
                                 attributeName: String,
                                 attributeValue: (T) -> String,
                                 operation: () -> T): T {
  return tracer.spanBuilder(spanName).useWithScopeBlocking { span ->
    val result = operation.invoke()
    span.setAttribute(attributeName, attributeValue.invoke(result))
    result
  }
}

fun <T> computeWithSpanAttributes(tracer: IJTracer,
                                  spanName: String,
                                  attributeGenerator: (T) -> Map<String, String>,
                                  operation: () -> T): T {
  return tracer.spanBuilder(spanName).useWithScopeBlocking { span ->
    val result = operation.invoke()
    attributeGenerator.invoke(result).forEach { (attributeName, attributeValue) ->
      span.setAttribute(attributeName, attributeValue)
    }
    result
  }
}

inline fun <T> computeWithSpan(tracer: Tracer, spanName: String, operation: (Span) -> T): T {
  return tracer.spanBuilder(spanName).useWithScopeBlocking(operation)
}

internal fun <T> computeWithSpanIgnoreThrows(tracer: Tracer,
                                             spanName: String,
                                             operation: ThrowableNotNullFunction<Span, T, out Throwable>): T {
  return tracer.spanBuilder(spanName).useWithScopeBlocking(operation::`fun`)
}

internal fun runWithSpanIgnoreThrows(tracer: Tracer, spanName: String, operation: ThrowableConsumer<Span, out Throwable>) {
  tracer.spanBuilder(spanName).useWithScopeBlocking(operation::consume)
}

fun runWithSpan(tracer: Tracer, spanName: String, operation: Consumer<Span>) {
  tracer.spanBuilder(spanName).useWithScopeBlocking(operation::accept)
}

fun runWithSpan(tracer: Tracer, spanName: String, parentSpan: Span, operation: Consumer<Span>) {
  tracer.spanBuilder(spanName).setParent(Context.current().with(parentSpan)).useWithScopeBlocking(operation::accept)
}

inline fun <T> SpanBuilder.use(operation: (Span) -> T): T {
  return startSpan().use(operation)
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