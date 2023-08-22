// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry

import com.intellij.openapi.util.ShutDownTracker
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.platform.diagnostic.telemetry.otExporters.CsvMetricsExporter
import com.intellij.util.ConcurrencyUtil
import com.intellij.util.concurrency.SynchronizedClearableLazy
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.common.AttributesBuilder
import io.opentelemetry.sdk.OpenTelemetrySdkBuilder
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

@ApiStatus.Internal
class OpenTelemetryConfigurator(private val mainScope: CoroutineScope,
                                private val sdkBuilder: OpenTelemetrySdkBuilder,
                                serviceName: String = "",
                                serviceVersion: String = "",
                                serviceNamespace: String = "",
                                customResourceBuilder: ((AttributesBuilder) -> Unit)? = null,
                                enableMetricsByDefault: Boolean) {
  private val metricsReportingPath = if (enableMetricsByDefault) OpenTelemetryUtils.metricsReportingPath() else null
  val resource: Resource = Resource.create(
    Attributes.builder()
      .put(ResourceAttributes.SERVICE_NAME, serviceName)
      .put(ResourceAttributes.SERVICE_VERSION, serviceVersion)
      .put(ResourceAttributes.SERVICE_NAMESPACE, serviceNamespace)
      .put(ResourceAttributes.OS_TYPE, SystemInfoRt.OS_NAME)
      .put(ResourceAttributes.OS_VERSION, SystemInfoRt.OS_VERSION)
      .put(ResourceAttributes.HOST_ARCH, System.getProperty("os.arch"))
      .put(ResourceAttributes.SERVICE_INSTANCE_ID, DateTimeFormatter.ISO_INSTANT.format(Instant.now()))
      .also {
        customResourceBuilder?.invoke(it)
      }
      .build()
  )

  val aggregatedMetricExporter: AggregatedMetricExporter = AggregatedMetricExporter()

  private fun isMetricsEnabled(): Boolean = metricsReportingPath != null

  fun registerSpanExporters(spanExporters: List<AsyncSpanExporter>) {
    if (spanExporters.isEmpty()) {
      return
    }

    val tracerProvider = SdkTracerProvider.builder()
      .addSpanProcessor(BatchSpanProcessor(coroutineScope = mainScope, spanExporters = spanExporters))
      .setResource(resource)
      .build()

    sdkBuilder.setTracerProvider(tracerProvider)
  }

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
    metricsReportingPath ?: return emptyList()

    val result = mutableListOf<MetricsExporterEntry>()
    result.add(MetricsExporterEntry(
      metrics = listOf(
        FilteredMetricsExporter(
          underlyingExporter = SynchronizedClearableLazy {
            CsvMetricsExporter(writeToFileSupplier = RollingFileSupplier(metricsReportingPath))
          },
          predicate = { metric -> metric.belongsToScope(PlatformMetrics) },
        ),
      ),
      duration = 1.minutes)
    )

    result.add(MetricsExporterEntry(listOf(aggregatedMetricExporter), 1.minutes))
    return result
  }

  fun getConfiguredSdkBuilder(): OpenTelemetrySdkBuilder {
    if (isMetricsEnabled()) {
      registerMetricExporters(createMetricsExporters())
    }
    return sdkBuilder
  }
}