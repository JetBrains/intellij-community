// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.tasks

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import java.util.concurrent.Callable
import java.util.concurrent.ForkJoinTask
import java.util.function.Supplier

internal val tracer: Tracer by lazy { GlobalOpenTelemetry.getTracer("build-script") }

fun <T> createTask(spanBuilder: SpanBuilder, task: Supplier<T>): ForkJoinTask<T> {
  val context = Context.current()
  return ForkJoinTask.adapt(Callable {
    val thread = Thread.currentThread()
    val span = spanBuilder
      .setParent(context)
      .setAttribute(SemanticAttributes.THREAD_NAME, thread.name)
      .setAttribute(SemanticAttributes.THREAD_ID, thread.id)
      .startSpan()
    span.makeCurrent().use {
      span.use {
        task.get()
      }
    }
  })
}

internal inline fun task(spanBuilder: SpanBuilder, crossinline operation: () -> Unit): ForkJoinTask<*> {
  val context = Context.current()
  return ForkJoinTask.adapt(Runnable {
    val thread = Thread.currentThread()
    spanBuilder
      .setParent(context)
      .setAttribute(SemanticAttributes.THREAD_NAME, thread.name)
      .setAttribute(SemanticAttributes.THREAD_ID, thread.id)
      .startSpan()
      .useWithScope {
        operation()
      }
  })
}

inline fun <T> Span.useWithScope(operation: (Span) -> T): T {
  return makeCurrent().use {
    use {
      operation(it)
    }
  }
}

inline fun <T> Span.use(operation: (Span) -> T): T {
  try {
    return operation(this)
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