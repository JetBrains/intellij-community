// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.jvm

import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.semconv.ExceptionAttributes
import java.util.concurrent.CancellationException

//fun getExceptionAttributes(e: Throwable): Attributes {
//  return Attributes.of(
//    ExceptionAttributes.EXCEPTION_MESSAGE, e.message ?: "",
//    ExceptionAttributes.EXCEPTION_STACKTRACE, e.stackTraceToString()
//  )
//}

inline fun <T> SpanBuilder.use(block: (Span) -> T): T {
  val span = startSpan()
  try {
    return block(span)
  }
  catch (e: CancellationException) {
    span.recordException(e, Attributes.of(ExceptionAttributes.EXCEPTION_ESCAPED, true))
    throw e
  }
  catch (e: Throwable) {
    span.recordException(e, Attributes.of(ExceptionAttributes.EXCEPTION_ESCAPED, true))
    span.setStatus(StatusCode.ERROR)
    throw e
  }
  finally {
    span.end()
  }
}