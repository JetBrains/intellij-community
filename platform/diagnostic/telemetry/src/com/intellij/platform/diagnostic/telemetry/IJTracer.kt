// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry

import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.api.trace.TracerProvider
import org.jetbrains.annotations.ApiStatus

/**
 * The interface solves two problems:
 * — Isolate IJ code base from OT classes
 * — Provide a more flexible way to handle spans. In some cases, we want to control how detailed OT information is,
 *    and in this case, the alternative signature can be used.
 */
@ApiStatus.Internal
interface IJTracer : Tracer {
  fun spanBuilder(spanName: String, level: TracerLevel): SpanBuilder
}

/**
 * Defines level of details for the tracing information.
 * In most cases, we don't need very-deep information about all possible subsystems.
 */
enum class TracerLevel {
  DEFAULT,
  DETAILED,
}

@ApiStatus.Internal
object IJNoopTracer : IJTracer {
  @JvmField
  val noopTrace: Tracer = TracerProvider.noop().get("")

  override fun spanBuilder(spanName: String): SpanBuilder = noopTrace.spanBuilder(spanName)

  override fun spanBuilder(spanName: String, level: TracerLevel): SpanBuilder = spanBuilder(spanName)
}