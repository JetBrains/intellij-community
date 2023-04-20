// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic.telemetry

import io.opentelemetry.sdk.metrics.export.MetricExporter
import java.time.Duration

class RdctExportersProvider : OTelExportersProvider {
  private val duration = System.getProperty("rdct.connection.metrics.interval")?.toLong()
  override fun getSpanExporters(): List<AsyncSpanExporter> {
    val spanExporters = mutableListOf<AsyncSpanExporter>()
    val rdctOtlpEndpoint = System.getProperty("rdct.diagnostic.otlp")
    rdctOtlpEndpoint?.let {
      spanExporters.add(MessageBusSpanExporter())
    }
    return spanExporters
  }

  override fun getMetricsExporters(): List<MetricExporter> {
    val metricsExporters = mutableListOf<MetricExporter>()
    val connectionMetricsFlag = System.getProperty("rdct.connection.metrics.enabled")
    val luxMetricsFlag = System.getProperty("lux.metrics.enabled")

    luxMetricsFlag?.let {
      metricsExporters.add(
        FilteredMetricsExporter(CsvGzippedMetricsExporter(CsvGzippedMetricsExporter.generateFileForLuxMetrics())) { metric ->
          metric.name.contains("lux")
        })
    }
    connectionMetricsFlag?.let {
      metricsExporters.add(
        FilteredMetricsExporter(CsvGzippedMetricsExporter(CsvGzippedMetricsExporter.generateFileForConnectionMetrics())) { metric ->
          metric.name.contains("rdct")
        })
    }
    return metricsExporters
  }

  override fun getReadsInterval(): Duration {
    return duration?.let { Duration.ofSeconds(it) } ?: Duration.ofSeconds(1)
  }
}