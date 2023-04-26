// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic.telemetry

import io.opentelemetry.sdk.metrics.export.MetricExporter
import java.time.Duration

interface OTelExportersProvider {
  fun getSpanExporters(): List<AsyncSpanExporter>
  fun getMetricsExporters(): List<MetricExporter>
  fun getReadsInterval(): Duration = Duration.ofMinutes(1)
}