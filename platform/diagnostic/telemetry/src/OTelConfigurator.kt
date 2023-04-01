// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic.telemetry

import com.intellij.diagnostic.telemetry.OpenTelemetryUtils.IDEA_DIAGNOSTIC_OTLP
import com.intellij.diagnostic.telemetry.otExporters.AggregatedMetricsExporter
import com.intellij.diagnostic.telemetry.otExporters.AggregatedSpansProcessor
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.util.ShutDownTracker
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.sdk.OpenTelemetrySdkBuilder
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes
import kotlinx.coroutines.CoroutineScope
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

class OTelConfigurator(private val mainScope: CoroutineScope,
                       private val otelSdkBuilder: OpenTelemetrySdkBuilder,
                       appInfo: ApplicationInfo,
                       enableMetricsByDefault: Boolean) {
  private val serviceName = ApplicationNamesInfo.getInstance().fullProductName
  private val serviceVersion = appInfo.build.asStringWithoutProductCode()
  private val serviceNamespace = appInfo.build.productCode
  private val metricsReportingPath = if (enableMetricsByDefault) OpenTelemetryUtils.metricsReportingPath() else null
  private val shutdownCompletionTimeout: Long = 10
  private val resource = Resource.create(Attributes.of(
    ResourceAttributes.SERVICE_NAME, serviceName,
    ResourceAttributes.SERVICE_VERSION, serviceVersion,
    ResourceAttributes.SERVICE_NAMESPACE, serviceNamespace,
    ResourceAttributes.SERVICE_INSTANCE_ID, DateTimeFormatter.ISO_INSTANT.format(Instant.now()),
  ))
  val aggregatedMetricsExporter = AggregatedMetricsExporter()
  val aggregatedSpansProcessor = AggregatedSpansProcessor(mainScope)
  private val spanExporters = mutableListOf<AsyncSpanExporter>()
  private val metricsExporters = mutableListOf<MetricsExporterEntry>()

  init {
    defaultSpanExportersRegistration()
    if (metricsEnabled()) {
      defaultMetricsExportersRegistration()
    }
  }

  private fun defaultMetricsExportersRegistration() {
    metricsReportingPath ?: return
    metricsExporters.add(MetricsExporterEntry(listOf(FilteredMetricsExporter(CsvMetricsExporter(RollingFileSupplier(metricsReportingPath))) { metric ->
      metric.belongsToScope(PLATFORM_METRICS)
    }), Duration.ofMinutes(1)))
    metricsExporters.add(MetricsExporterEntry(listOf(aggregatedMetricsExporter), Duration.ofSeconds(1)))
    registerMetricsExporter()
  }

  private fun defaultSpanExportersRegistration() {
    val traceFile = System.getProperty("idea.diagnostic.opentelemetry.file")
    val traceEndpoint = System.getProperty(IDEA_DIAGNOSTIC_OTLP)
    if (traceFile != null) {
      spanExporters.add(JaegerJsonSpanExporter(Path.of(traceFile), serviceName, serviceVersion, serviceNamespace))
    }
    if (traceEndpoint != null) {
      spanExporters.add(OtlpSpanExporter(traceEndpoint))
    }
    registerSpanExporters()
  }

  fun getConfiguredSdkBuilder(): OpenTelemetrySdkBuilder {
    return otelSdkBuilder
  }

  private fun registerMetricsExporter() {
    val registeredMetricsReaders = SdkMeterProvider.builder()
    for (entry in metricsExporters) {
      for (it in entry.metrics) {
        val metricsReader = PeriodicMetricReader.builder(it).setInterval(entry.duration).build()
        registeredMetricsReaders.registerMetricReader(metricsReader)
      }
    }
    val meterProvider = registeredMetricsReaders.setResource(resource).build()
    otelSdkBuilder.setMeterProvider(meterProvider)
    ShutDownTracker.getInstance().registerShutdownTask(meterProvider::shutdown)
  }

  fun addMetricsExporters(extraMetricsExporters: List<MetricsExporterEntry>) {
    if (extraMetricsExporters.isEmpty()) return
    for (exporter in extraMetricsExporters) {
      if (!metricsExporters.contains(exporter)) metricsExporters.add(exporter)
    }
    registerMetricsExporter()
  }

  fun addSpanExporters(extraSpanExporters: List<AsyncSpanExporter>) {
    if (extraSpanExporters.isEmpty()) return
    for (exporter in extraSpanExporters) {
      if (!spanExporters.contains(exporter)) spanExporters.add(exporter)
    }
    registerSpanExporters()
  }

  private fun registerSpanExporters() {
    if (spanExporters.isNotEmpty()) {
      val tracerProvider = SdkTracerProvider.builder().addSpanProcessor(BatchSpanProcessor(mainScope, spanExporters))
        .addSpanProcessor(aggregatedSpansProcessor)
        .setResource(resource).build()

      otelSdkBuilder.setTracerProvider(tracerProvider)
      ShutDownTracker.getInstance().registerShutdownTask {
        tracerProvider.shutdown().join(shutdownCompletionTimeout, TimeUnit.SECONDS)
      }
    }
  }

  private fun metricsEnabled(): Boolean {
    return metricsReportingPath != null
  }
}