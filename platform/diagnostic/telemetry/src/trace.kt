// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic.telemetry

import com.intellij.openapi.util.ThrowableNotNullFunction
import com.intellij.util.ThrowableConsumer
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import io.opentelemetry.extension.kotlin.asContextElement
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import kotlinx.coroutines.withContext
import java.util.concurrent.Callable
import java.util.concurrent.CancellationException
import java.util.concurrent.ForkJoinTask
import java.util.function.Consumer

/**
 * Returns a new [ForkJoinTask] that performs the given function as its action within a trace, and returns
 * a null result upon [ForkJoinTask.join].
 *
 * See [Span](https://opentelemetry.io/docs/reference/specification).
 */
inline fun <T> forkJoinTask(spanBuilder: SpanBuilder, crossinline operation: () -> T): ForkJoinTask<T> {
  val context = Context.current()
  return ForkJoinTask.adapt(Callable {
    val thread = Thread.currentThread()
    spanBuilder
      .setParent(context)
      .setAttribute(SemanticAttributes.THREAD_NAME, thread.name)
      .setAttribute(SemanticAttributes.THREAD_ID, thread.id)
      .useWithScope {
        operation()
      }
  })
}

inline fun <T> SpanBuilder.useWithScope(operation: (Span) -> T): T {
  val span = startSpan()
  return span.makeCurrent().use {
    span.use(operation)
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
  catch (e: Throwable) {
    recordException(e)
    setStatus(StatusCode.ERROR)
    throw e
  }
  finally {
    end()
  }
}