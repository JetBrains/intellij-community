// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry.helpers

import com.intellij.openapi.util.ThrowableNotNullFunction
import com.intellij.util.ThrowableConsumer
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.context.Context
import io.opentelemetry.extension.kotlin.asContextElement
import io.opentelemetry.semconv.SemanticAttributes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.concurrent.CancellationException
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Starts a new span and adds it to the current scope for the [operation].
 * That way the spans created inside the [operation] will be nested to the created span.
 *
 * See [span concept](https://opentelemetry.io/docs/concepts/signals/traces/#spans) for more details on span nesting.
 */
@Internal
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
@Internal
inline fun <T> Span.use(operation: (Span) -> T): T {
  return useWithoutActiveScope {
    makeCurrent().use {
      operation(this)
    }
  }
}

@Internal
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

@Internal
internal fun <T> computeWithSpanIgnoreThrows(spanBuilder: SpanBuilder,
                                             operation: ThrowableNotNullFunction<Span, T, out Throwable>): T {
  return spanBuilder.use(operation::`fun`)
}

@Internal
internal fun runWithSpanIgnoreThrows(spanBuilder: SpanBuilder, operation: ThrowableConsumer<Span, out Throwable>) {
  spanBuilder.use(operation::consume)
}

/**
 * Does not activate the span scope, so **new spans created inside [operation] will not be linked to [this] span**.
 * [Span] supplied as an argument to [operation] may not be the [Span.current].
 * No overhead of [Context.makeCurrent] is incurred.
 *
 * Consider using [use] to also activate the scope.
 */
@PublishedApi
@Internal
internal inline fun <T> Span.useWithoutActiveScope(operation: (Span) -> T): T {
  try {
    return operation(this)
  }
  catch (e: CancellationException) {
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