// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry.impl

import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.context.Context
import io.opentelemetry.extension.kotlin.asContextElement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

suspend inline fun <T> SpanBuilder.useWithScope2(crossinline operation: suspend (Span) -> T): T {
  val span = startSpan()
  return withContext(Context.current().with(span).asContextElement()) {
    span.use {
      operation(span)
    }
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