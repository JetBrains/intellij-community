// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.api.trace.TracerProvider


fun wrapTracer(scopeName: String, tracer: Tracer, verbose: Boolean, verboseMode: Boolean): IJTracer {
  if (verbose && !verboseMode) return IJNoopTracer
  if (tracer == IJNoopTracer.noopTrace) return IJNoopTracer

  return TraceWrapper(scopeName, tracer, emptySet(), verbose)
}


private class TraceWrapper(private val scopeName: String,
                           private val tracer: Tracer,
                           private val detailedTracers: Set<String>,
                           private val verbose: Boolean) : IJTracer {
  override fun spanBuilder(spanName: String): SpanBuilder = tracer.spanBuilder(spanName)

  override fun spanBuilder(spanName: String, level: TracerLevel): SpanBuilder {
    if (level == TracerLevel.DETAILED) {
      if (!verbose && !detailedTracers.contains(scopeName)) {
        return OpenTelemetry.noop().getTracer(scopeName).spanBuilder(spanName)
      }
    }

    return spanBuilder(spanName)
  }
}

object IJNoopTracer : IJTracer {
  val noopTrace: Tracer = TracerProvider.noop().get("")

  override fun spanBuilder(spanName: String): SpanBuilder = noopTrace.spanBuilder(spanName)
  override fun spanBuilder(spanName: String, level: TracerLevel): SpanBuilder = spanBuilder(spanName)
}


