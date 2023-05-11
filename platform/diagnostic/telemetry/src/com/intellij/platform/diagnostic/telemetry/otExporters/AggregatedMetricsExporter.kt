// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry.otExporters

import com.intellij.platform.diagnostic.telemetry.MetricsExporterEntry
import com.intellij.openapi.diagnostic.logger
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.metrics.InstrumentType
import io.opentelemetry.sdk.metrics.data.AggregationTemporality
import io.opentelemetry.sdk.metrics.data.MetricData
import io.opentelemetry.sdk.metrics.export.MetricExporter
import java.util.concurrent.CopyOnWriteArrayList

class AggregatedMetricsExporter : MetricExporter {

  companion object {
    val LOG = logger<AggregatedMetricsExporter>()
  }

  private val metricsExporters: CopyOnWriteArrayList<MetricsExporterEntry> = CopyOnWriteArrayList<MetricsExporterEntry>()

  fun addMetricsExporters(vararg metrics: MetricsExporterEntry) {
    metricsExporters.addAll(metrics)
  }

  override fun getAggregationTemporality(instrumentType: InstrumentType): AggregationTemporality {
    return AggregationTemporality.DELTA
  }

  override fun export(metrics: MutableCollection<MetricData>): CompletableResultCode {
    if (metricsExporters.isEmpty()) return CompletableResultCode.ofSuccess()
    val results = mutableListOf<CompletableResultCode>()
    for (entry in metricsExporters) {
      for (exporter in entry.metrics) {
        try {
          results.add(exporter.export(metrics))
        }
        catch (e: Throwable) {
          results.add(CompletableResultCode.ofFailure())
          LOG.info("Exporter ${exporter.javaClass.name} failed to export with: ${e.message}")
        }
      }
    }
    return CompletableResultCode.ofAll(results)
  }

  override fun flush(): CompletableResultCode {
    val results = mutableListOf<CompletableResultCode>()
    for (metricsExporterEntry in metricsExporters) {
      for (exporter in metricsExporterEntry.metrics) {
        try {
          results.add(exporter.flush())
        }
        catch (e: Throwable) {
          results.add(CompletableResultCode.ofFailure())
          LOG.info("Exporter ${exporter.javaClass.name} failed to flush with: ${e.message}")
        }
      }
    }
    return CompletableResultCode.ofAll(results)
  }

  override fun shutdown(): CompletableResultCode {
    val results = mutableListOf<CompletableResultCode>()
    for (metricsExporterEntry in metricsExporters) {
      for (exporter in metricsExporterEntry.metrics) {
        try {
          results.add(exporter.shutdown())
        }
        catch (e: Throwable) {
          results.add(CompletableResultCode.ofFailure())
          LOG.info("Exporter ${exporter.javaClass.name} failed to shutdown with: ${e.message}")
        }
      }
    }
    return CompletableResultCode.ofAll(results)
  }
}