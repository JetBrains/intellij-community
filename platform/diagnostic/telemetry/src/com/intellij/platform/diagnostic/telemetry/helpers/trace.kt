// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry.helpers

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.ThrowableNotNullFunction
import com.intellij.platform.diagnostic.telemetry.IJTracer
import com.intellij.util.ThrowableConsumer
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import io.opentelemetry.extension.kotlin.asContextElement
import io.opentelemetry.semconv.SemanticAttributes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import java.util.concurrent.CancellationException
import java.util.function.Consumer
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Starts a new span and adds it to the current scope for the [operation].
 * That way the spans created inside the [operation] will be nested to the created span.
 *
 * See [span concept](https://opentelemetry.io/docs/concepts/signals/traces/#spans) for more details on span nesting.
 */
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
inline fun <T> Span.use(operation: (Span) -> T): T {
  return useWithoutActiveScope {
    makeCurrent().use {
      operation(this)
    }
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
      span.recordException(e, Attributes.of(SemanticAttributes.EXCEPTION_ESCAPED, true))
      throw e
    }
    catch (e: Throwable) {
      span.recordException(e, Attributes.of(SemanticAttributes.EXCEPTION_ESCAPED, true))
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
  return tracer.spanBuilder(spanName).use { span ->
    val result = operation.invoke()
    span.setAttribute(attributeName, attributeValue.invoke(result))
    result
  }
}

fun <T> computeWithSpanAttributes(tracer: IJTracer,
                                  spanName: String,
                                  attributeGenerator: (T) -> Map<String, String>,
                                  operation: () -> T): T {
  return tracer.spanBuilder(spanName).use { span ->
    val result = operation.invoke()
    attributeGenerator.invoke(result).forEach { (attributeName, attributeValue) ->
      span.setAttribute(attributeName, attributeValue)
    }
    result
  }
}

inline fun <T> computeWithSpan(tracer: Tracer, spanName: String, operation: (Span) -> T): T {
  return tracer.spanBuilder(spanName).use(operation)
}

internal fun <T> computeWithSpanIgnoreThrows(tracer: Tracer,
                                             spanName: String,
                                             operation: ThrowableNotNullFunction<Span, T, out Throwable>): T {
  return tracer.spanBuilder(spanName).use(operation::`fun`)
}

internal fun runWithSpanIgnoreThrows(tracer: Tracer, spanName: String, operation: ThrowableConsumer<Span, out Throwable>) {
  tracer.spanBuilder(spanName).use(operation::consume)
}

fun runWithSpan(tracer: Tracer, spanName: String, operation: Consumer<Span>) {
  tracer.spanBuilder(spanName).use(operation::accept)
}

/**
 * Does not activate the span scope, so **new spans created inside will not be linked to the started span**.
 * Consider using [use] to also activate the scope.
 */
inline fun <T> SpanBuilder.useWithoutActiveScope(operation: (Span) -> T): T {
  return startSpan().useWithoutActiveScope(operation)
}

/**
 * Does not activate the span scope, so **new spans created inside will not be linked to [this] span**.
 * Consider using [use] to also activate the scope.
 */
inline fun <T> Span.useWithoutActiveScope(operation: (Span) -> T): T {
  try {
    return operation(this)
  }
  catch (e: CancellationException) {
    recordException(e, Attributes.of(SemanticAttributes.EXCEPTION_ESCAPED, true))
    throw e
  }
  catch (e: ProcessCanceledException) {
    recordException(e, Attributes.of(SemanticAttributes.EXCEPTION_ESCAPED, true))
    throw e
  }
  catch (e: Throwable) {
    recordException(e, Attributes.of(SemanticAttributes.EXCEPTION_ESCAPED, true))
    setStatus(StatusCode.ERROR)
    throw e
  }
  finally {
    end()
  }
}