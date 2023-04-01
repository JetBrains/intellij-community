// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic.telemetry.otExporters

import com.intellij.diagnostic.telemetry.AsyncSpanExporter
import com.intellij.openapi.extensions.ExtensionPointName
import io.opentelemetry.sdk.metrics.export.MetricExporter
import java.time.Duration

/**
 * EP for custom spans and metrics providers. These providers will be loaded after the application startup.
 * By this time default platform providers will be already added.
 * For more details @see com.intellij.diagnostic.telemetry.OTelConfigurator
 */
interface OTelExportersProvider {
  companion object {
    val EP: ExtensionPointName<OTelExportersProvider> = ExtensionPointName("com.intellij.oTelExportersProvider")
  }

  fun getSpanExporters(): List<AsyncSpanExporter>
  fun getMetricsExporters(): List<MetricExporter>
  fun isTracingAvailable(): Boolean
  fun areMetricsAvailable(): Boolean
  fun getReadsInterval(): Duration = Duration.ofMinutes(1)
}