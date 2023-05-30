// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry

import com.intellij.openapi.util.ShutDownTracker
import com.intellij.platform.diagnostic.telemetry.otExporters.AggregatedMetricsExporter
import com.intellij.platform.diagnostic.telemetry.otExporters.AggregatedSpansProcessor
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.sdk.OpenTelemetrySdkBuilder
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.jetbrains.annotations.ApiStatus
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

@ApiStatus.Internal
open class OpenTelemetryDefaultConfigurator(protected val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Default),
                                            protected val otelSdkBuilder: OpenTelemetrySdkBuilder,
                                            protected val serviceName: String = "",
                                            protected val serviceVersion: String = "",
                                            protected val serviceNamespace: String = "",
                                            enableMetricsByDefault: Boolean) {

  private val metricsReportingPath = if (enableMetricsByDefault) OpenTelemetryUtils.metricsReportingPath() else null
  private val shutdownCompletionTimeout: Long = 10
  private val resource: Resource = Resource.create(Attributes.of(
    ResourceAttributes.SERVICE_NAME, serviceName,
    ResourceAttributes.SERVICE_VERSION, serviceVersion,
    ResourceAttributes.SERVICE_NAMESPACE, serviceNamespace,
    ResourceAttributes.SERVICE_INSTANCE_ID, DateTimeFormatter.ISO_INSTANT.format(Instant.now()),
  ))

  val aggregatedMetricsExporter: AggregatedMetricsExporter = AggregatedMetricsExporter()
  val aggregatedSpansProcessor: AggregatedSpansProcessor = AggregatedSpansProcessor(mainScope)
  protected val spanExporters: MutableList<AsyncSpanExporter> = mutableListOf<AsyncSpanExporter>()
  private val metricsExporters = mutableListOf<MetricsExporterEntry>()

  private fun isMetricsEnabled(): Boolean {
    return metricsReportingPath != null
  }

  private fun registerSpanExporters() {
    if (spanExporters.isNotEmpty()) {
      val tracerProvider = SdkTracerProvider.builder().addSpanProcessor(BatchSpanProcessor(mainScope, spanExporters))
        .addSpanProcessor(aggregatedSpansProcessor)
        .setResource(resource)
        .build()

      otelSdkBuilder.setTracerProvider(tracerProvider)
      ShutDownTracker.getInstance().registerShutdownTask {
        tracerProvider.shutdown().join(shutdownCompletionTimeout, TimeUnit.SECONDS)
      }
    }
  }

  private fun registerMetricsExporter() {
    val registeredMetricsReaders = SdkMeterProvider.builder()
    metricsExporters.forEach { entry ->
      entry.metrics.forEach {
        val metricsReader = PeriodicMetricReader.builder(it).setInterval(entry.duration).build()
        registeredMetricsReaders.registerMetricReader(metricsReader)
      }
    }
    val meterProvider = registeredMetricsReaders.setResource(resource).build()
    otelSdkBuilder.setMeterProvider(meterProvider)
    ShutDownTracker.getInstance().registerShutdownTask(meterProvider::shutdown)
  }

  open fun getDefaultSpanExporters(): List<AsyncSpanExporter> = spanExporters

  open fun getDefaultMetricsExporters(): List<MetricsExporterEntry> {
    metricsReportingPath ?: return emptyList()

    metricsExporters.add(
      MetricsExporterEntry(
        metrics = listOf(
          FilteredMetricsExporter(
            CsvMetricsExporter(RollingFileSupplier(metricsReportingPath))) { metric -> metric.belongsToScope(PlatformMetrics) }
        ),
        duration = Duration.ofMinutes(1))
    )

    metricsExporters.add(MetricsExporterEntry(listOf(aggregatedMetricsExporter), Duration.ofMinutes(1)))

    return metricsExporters
  }

  open fun configureSpanExporters(): List<AsyncSpanExporter> = getDefaultSpanExporters()

  open fun configureMetricsExporter(): List<MetricsExporterEntry> = getDefaultMetricsExporters()

  fun getConfiguredSdkBuilder(): OpenTelemetrySdkBuilder {
    val metricsEnabled = isMetricsEnabled()

    configureSpanExporters()
    configureMetricsExporter()

    registerSpanExporters()
    if (metricsEnabled) {
      registerMetricsExporter()
    }

    return otelSdkBuilder
  }
}