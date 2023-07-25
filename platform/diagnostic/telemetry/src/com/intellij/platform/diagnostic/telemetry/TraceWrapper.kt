// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.api.trace.TracerProvider
import org.jetbrains.annotations.ApiStatus.Internal
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

@Internal
fun wrapTracer(scopeName: String, tracer: Tracer, verbose: Boolean, verboseMode: Boolean): IJTracer {
  if ((verbose && !verboseMode) || tracer == IJNoopTracer.noopTrace) {
    return IJNoopTracer
  }
  else {
    return TraceWrapper(scopeName = scopeName, tracer = tracer, detailedTracers = emptySet(), verbose = verbose)
  }
}

private class TraceWrapper(private val scopeName: String,
                           private val tracer: Tracer,
                           private val detailedTracers: Set<String>,
                           private val verbose: Boolean) : IJTracer {
  override fun spanBuilder(spanName: String): SpanBuilder = tracer.spanBuilder(spanName)

  override fun spanBuilder(spanName: String, level: TracerLevel): SpanBuilder {
    if (level == TracerLevel.DETAILED && !verbose && !detailedTracers.contains(scopeName)) {
      return OpenTelemetry.noop().getTracer(scopeName).spanBuilder(spanName)
    }
    return spanBuilder(spanName)
  }
}

@Internal
object IJNoopTracer : IJTracer {
  @JvmField
  internal val noopTrace: Tracer = TracerProvider.noop().get("")

  override fun spanBuilder(spanName: String): SpanBuilder = noopTrace.spanBuilder(spanName)

  override fun spanBuilder(spanName: String, level: TracerLevel): SpanBuilder = spanBuilder(spanName)
}

@Internal
object NoopIntelliJTracer : IntelliJTracer {
  override fun createSpan(name: String): CoroutineContext = EmptyCoroutineContext
}


