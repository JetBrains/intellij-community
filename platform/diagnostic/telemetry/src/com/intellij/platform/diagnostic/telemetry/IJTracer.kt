// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry

import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.Tracer

/**
 * The interface solves two problems:
 * — Isolate IJ code base from OT classes
 * — Provide a more flexible way to handle spans. In some cases we want to control how detailed OT information,
 *    and in this case the alternative signature can be used.
 */
interface IJTracer : Tracer {
  fun spanBuilder(spanName: String, level: TracerLevel): SpanBuilder
}