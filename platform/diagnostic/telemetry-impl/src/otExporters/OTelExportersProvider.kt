// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry.impl.otExporters

import com.intellij.platform.diagnostic.telemetry.AsyncSpanExporter
import io.opentelemetry.sdk.metrics.export.MetricExporter
import java.time.Duration

/**
 * EP for custom spans and metrics providers. These providers will be loaded after the application startup.
 * By this time, default platform providers will be already added.
 */
interface OTelExportersProvider {
  fun getSpanExporters(): List<AsyncSpanExporter>

  fun getMetricsExporters(): List<MetricExporter>

  fun isTracingAvailable(): Boolean

  fun areMetricsAvailable(): Boolean

  fun getReadInterval(): Duration = Duration.ofMinutes(1)
}