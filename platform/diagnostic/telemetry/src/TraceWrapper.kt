// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic.telemetry

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.api.trace.TracerProvider


/**
 * The interface solves two problems:
 * — Isolate IJ code base from OT classes
 * — Provide a more flexible way to handle spans. In some cases we want to control how detailed OT information,
 *    and in this case the alternative signature can be used.
 */
interface IJTracer : Tracer {
  fun spanBuilder(spanName: String, level: TracerLevel): SpanBuilder
}

/**
 * Defines level of details for the tracing information.
 * In most cases we don't need very-deep information about all possible subsystems.
 */
enum class TracerLevel {
  DEFAULT,
  DETAILED,
}

internal fun wrapTracer(scopeName: String, tracer: Tracer, verbose: Boolean, verboseMode: Boolean): IJTracer {
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


