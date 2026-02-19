// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry.impl

import com.intellij.platform.diagnostic.telemetry.IJNoopTracer
import com.intellij.platform.diagnostic.telemetry.IJTracer
import com.intellij.platform.diagnostic.telemetry.TracerLevel
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.Tracer

internal fun wrapTracer(scopeName: String, tracer: Tracer, verbose: Boolean, verboseMode: Boolean): IJTracer {
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
