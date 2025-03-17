// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.tracing

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.metrics.Meter
import io.opentelemetry.api.trace.Tracer

private const val FLEET_INSTRUMENTER_NAME = "fleet"

//this is a single instance that they recommend to use in OTel docs
val opentelemetry: OpenTelemetry by lazy { GlobalOpenTelemetry.get() }

val tracer: Tracer
  get() = opentelemetry.getTracer(FLEET_INSTRUMENTER_NAME)

val meter: Meter
  get() = opentelemetry.getMeter(FLEET_INSTRUMENTER_NAME)

//@fleet.kernel.plugins.InternalInPluginModules(where = ["fleet.reporting.opentelemetry"])
const val CANCELLED_SPAN_ATTR = "Cancelled"
