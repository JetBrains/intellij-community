// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry

import io.opentelemetry.api.metrics.Meter


fun Scope.meter(): Meter = TelemetryTracer.getInstance().getMeter(this.toString())
fun Scope.tracer(verbose: Boolean): IJTracer = TelemetryTracer.getInstance().getTracer(this.toString(), verbose)