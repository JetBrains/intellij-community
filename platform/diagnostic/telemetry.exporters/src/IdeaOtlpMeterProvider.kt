// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry.exporters

import com.intellij.openapi.util.ShutDownTracker
import com.intellij.platform.diagnostic.telemetry.AggregatedMetricExporter
import com.intellij.platform.diagnostic.telemetry.MetricsExporterEntry
import com.intellij.platform.diagnostic.telemetry.OpenTelemetryUtils
import com.intellij.platform.diagnostic.telemetry.exporters.meters.CsvMetricsExporter
import com.intellij.platform.diagnostic.telemetry.exporters.meters.TelemetryMeterJsonExporter
import com.intellij.util.ConcurrencyUtil
import com.intellij.util.SystemProperties.getLongProperty
import com.intellij.util.containers.addIfNotNull
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.metrics.SdkMeterProviderBuilder
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader
import io.opentelemetry.sdk.resources.Resource
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import java.util.concurrent.Executors
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

private val DEFAULT_METRICS_REPORTING_PERIOD: Duration = 1.minutes

private val CSV_METRICS_REPORTING_PERIOD: Duration = getLongProperty(
  "idea.diagnostic.opentelemetry.metrics-reporting-period-ms",
  DEFAULT_METRICS_REPORTING_PERIOD.inWholeMilliseconds
).milliseconds

@ApiStatus.Internal
object IdeaOtlpMeterProvider {

  fun get(resource: Resource, aggregatedExporter: AggregatedMetricExporter): SdkMeterProvider {
    val registeredMetricsReaders = SdkMeterProvider.builder()
    // can't reuse standard BoundedScheduledExecutorService because this library uses unsupported `scheduleAtFixedRate`
    val pool = Executors.newScheduledThreadPool(1, ConcurrencyUtil.newNamedThreadFactory("PeriodicMetricReader"))
    val metricsExporters: List<MetricsExporterEntry> = createMetricsExporters(aggregatedExporter)
    for (entry in metricsExporters) {
      for (metricExporter in entry.metrics) {
        val metricsReader = PeriodicMetricReader.builder(metricExporter)
          .setExecutor(pool)
          .setInterval(entry.duration.toJavaDuration())
          .build()
        registeredMetricsReaders.registerMetricReader(metricsReader)
      }
    }
    return registeredMetricsReaders.setResource(resource)
      .buildWithIdeLifecycle()
  }

  private fun createMetricsExporters(aggregatedMetricExporter: AggregatedMetricExporter): List<MetricsExporterEntry> {
    val exporters = mutableListOf<MetricsExporterEntry>()
    exporters.addIfNotNull(createCsvExporter())
    exporters.addIfNotNull(createJsonExporter())
    if (exporters.isNotEmpty()) {
      exporters.add(MetricsExporterEntry(listOf(aggregatedMetricExporter), DEFAULT_METRICS_REPORTING_PERIOD))
    }
    return exporters
  }

  // old metrics exporter to .csv file
  private fun createCsvExporter(): MetricsExporterEntry? {
    val metricsCsvPath: Path = OpenTelemetryUtils.metricsCsvReportingPath() ?: return null
    return MetricsExporterEntry(
      metrics = listOf(
        CsvMetricsExporter(RollingFileSupplier(metricsCsvPath, OpenTelemetryUtils.csvHeadersLines())),
      ),
      duration = CSV_METRICS_REPORTING_PERIOD
    )
  }

  // metrics exporter to .json file
  private fun createJsonExporter(): MetricsExporterEntry? {
    val metricsJsonPath: Path = OpenTelemetryUtils.metricsJsonReportingPath() ?: return null
    return MetricsExporterEntry(
      metrics = listOf(
        TelemetryMeterJsonExporter(RollingFileSupplier(basePath = metricsJsonPath, maxFilesToKeep = 30)),
      ),
      duration = DEFAULT_METRICS_REPORTING_PERIOD
    )
  }

  private fun SdkMeterProviderBuilder.buildWithIdeLifecycle(): SdkMeterProvider {
    val provider = build()
    ShutDownTracker.getInstance()
      .registerShutdownTask { provider.shutdown() }
    return provider
  }
}