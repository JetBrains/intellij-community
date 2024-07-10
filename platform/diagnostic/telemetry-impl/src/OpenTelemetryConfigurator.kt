// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry.impl

import com.intellij.openapi.util.ShutDownTracker
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.platform.diagnostic.telemetry.*
import com.intellij.platform.diagnostic.telemetry.exporters.RollingFileSupplier
import com.intellij.platform.diagnostic.telemetry.exporters.meters.CsvMetricsExporter
import com.intellij.platform.diagnostic.telemetry.exporters.meters.TelemetryMeterJsonExporter
import com.intellij.util.ConcurrencyUtil
import com.intellij.util.SystemProperties.getLongProperty
import com.intellij.util.concurrency.SynchronizedClearableLazy
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.common.AttributesBuilder
import io.opentelemetry.sdk.OpenTelemetrySdkBuilder
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader
import io.opentelemetry.sdk.resources.Resource
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

private val METRICS_REPORTING_PERIOD: Duration = getLongProperty(
  "idea.diagnostic.opentelemetry.metrics-reporting-period-ms",
  1.minutes.inWholeMilliseconds
).milliseconds

@ApiStatus.Internal
class OpenTelemetryConfigurator(@JvmField internal val sdkBuilder: OpenTelemetrySdkBuilder,
                                serviceName: String = "",
                                serviceVersion: String = "",
                                serviceNamespace: String = "",
                                customResourceBuilder: ((AttributesBuilder) -> Unit)? = null) {
  val resource: Resource = Resource.create(
    Attributes.builder()
      .put(AttributeKey.stringKey("service.name"), serviceName)
      .put(AttributeKey.stringKey("service.version"), serviceVersion)
      .put(AttributeKey.stringKey("service.namespace"), serviceNamespace)
      .put(AttributeKey.stringKey("os.type"), SystemInfoRt.OS_NAME)
      .put(AttributeKey.stringKey("os.version"), SystemInfoRt.OS_VERSION)
      .put(AttributeKey.stringKey("host.arch"), System.getProperty("os.arch"))
      .put(AttributeKey.stringKey("service.instance.id"), DateTimeFormatter.ISO_INSTANT.format(Instant.now()))
      .also {
        customResourceBuilder?.invoke(it)
      }
      .build()
  )

  val aggregatedMetricExporter: AggregatedMetricExporter = AggregatedMetricExporter()

  private fun registerMetricExporters(metricsExporters: List<MetricsExporterEntry>) {
    val registeredMetricsReaders = SdkMeterProvider.builder()
    // can't reuse standard BoundedScheduledExecutorService because this library uses unsupported `scheduleAtFixedRate`
    val pool = Executors.newScheduledThreadPool(1, ConcurrencyUtil.newNamedThreadFactory("PeriodicMetricReader"))
    for (entry in metricsExporters) {
      for (metricExporter in entry.metrics) {
        val metricsReader = PeriodicMetricReader.builder(metricExporter)
          .setExecutor(pool)
          .setInterval(entry.duration.toJavaDuration())
          .build()
        registeredMetricsReaders.registerMetricReader(metricsReader)
      }
    }
    val meterProvider = registeredMetricsReaders.setResource(resource).build()
    sdkBuilder.setMeterProvider(meterProvider)
    ShutDownTracker.getInstance().registerShutdownTask(meterProvider::shutdown)
  }

  private fun createMetricsExporters(): List<MetricsExporterEntry> {
    val metricsCsvPath: Path? = OpenTelemetryUtils.metricsCsvReportingPath()
    val metricsJsonPath: Path? = OpenTelemetryUtils.metricsJsonReportingPath()

    if (metricsCsvPath == null && metricsJsonPath == null) return emptyList()

    val exporters = mutableListOf<MetricsExporterEntry>()

    // old metrics exporter to .csv file
    metricsCsvPath?.let {
      exporters.add(
        MetricsExporterEntry(
          metrics = listOf(
            FilteredMetricsExporter(
              underlyingExporter = SynchronizedClearableLazy {
                CsvMetricsExporter(RollingFileSupplier(it, OpenTelemetryUtils.csvHeadersLines()))
              },
              predicate = { metric -> metric.belongsToScope(PlatformMetrics) },
            ),
          ),
          duration = METRICS_REPORTING_PERIOD
        )
      )
    }

    // metrics exporter to .json file
    metricsJsonPath?.let {
      exporters.add(MetricsExporterEntry(
        metrics = listOf(
          FilteredMetricsExporter(
            underlyingExporter = SynchronizedClearableLazy {
              TelemetryMeterJsonExporter(RollingFileSupplier(basePath = it, maxFilesToKeep = 30))
            },
            predicate = { metric -> metric.belongsToScope(PlatformMetrics) },
          ),
        ),
        duration = 1.minutes)
      )
    }

    exporters.add(MetricsExporterEntry(listOf(aggregatedMetricExporter), 1.minutes))
    return exporters
  }

  fun getConfiguredSdkBuilder(): OpenTelemetrySdkBuilder {
    registerMetricExporters(createMetricsExporters())
    return sdkBuilder
  }
}