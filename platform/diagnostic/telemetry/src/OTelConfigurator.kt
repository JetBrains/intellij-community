// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic.telemetry

import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.util.ShutDownTracker
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.sdk.OpenTelemetrySdkBuilder
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.metrics.export.MetricExporter
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

class OTelConfigurator(private val mainScope: CoroutineScope, private val otelSdkBuilder: OpenTelemetrySdkBuilder) {
  private val serviceName = ApplicationNamesInfo.getInstance().fullProductName
  private val appInfo = ApplicationInfoImpl.getShadowInstance()
  private val serviceVersion = appInfo.build.asStringWithoutProductCode()
  private val serviceNamespace = appInfo.build.productCode
  private val metricsReportingPath = MetricsExporterUtils.metricsReportingPath()
  private val resource = Resource.create(Attributes.of(
    ResourceAttributes.SERVICE_NAME, serviceName,
    ResourceAttributes.SERVICE_VERSION, serviceVersion,
    ResourceAttributes.SERVICE_NAMESPACE, serviceNamespace,
    ResourceAttributes.SERVICE_INSTANCE_ID, DateTimeFormatter.ISO_INSTANT.format(Instant.now()),
  ))

  init {
    val metricsEnabled = metricsEnabled()
    val metricsExporters = mutableListOf<MetricsExporterEntry>()
    val spanExporters = mutableListOf<AsyncSpanExporter>()
    spanExporters.addAll(getDefaultSpanExporters())
    metricsExporters.add(MetricsExporterEntry(getDefaultMetricsExporters(), Duration.ofMinutes(1)))

    getCustomOTelProviders().forEach { provider: OTelExportersProvider ->
      spanExporters.addAll(provider.getSpanExporters())
      val metrics = provider.getMetricsExporters()
      val duration = provider.getReadsInterval()
      metricsExporters.add(MetricsExporterEntry(metrics, duration))
    }
    registerSpanExporters(spanExporters)
    if (metricsEnabled) {
      registerMetricsExporter(metricsExporters)
    }
  }

  fun getConfiguredSdkBuilder(): OpenTelemetrySdkBuilder {
    return otelSdkBuilder
  }

  /**
   * Custom metrics and spans providers are added here
   * @see OTelExportersProvider
   */
  private fun getCustomOTelProviders(): List<OTelExportersProvider> {
    val providersList = mutableListOf<OTelExportersProvider>()
    providersList.add(RdctExportersProvider())
    return providersList
  }

  private fun registerMetricsExporter(entries: List<MetricsExporterEntry>) {
    val registeredMetricsReaders = SdkMeterProvider.builder()
    entries.forEach { entry ->
      entry.metrics.forEach {
        val metricsReader = PeriodicMetricReader.builder(it).setInterval(entry.duration).build()
        registeredMetricsReaders.registerMetricReader(metricsReader)
      }
    }
    val meterProvider = registeredMetricsReaders.setResource(resource).build()
    otelSdkBuilder.setMeterProvider(meterProvider)
    ShutDownTracker.getInstance().registerShutdownTask(meterProvider::shutdown)
  }

  private fun registerSpanExporters(spanExporters: List<AsyncSpanExporter>) {
    if (spanExporters.isNotEmpty()) {
      val tracerProvider = SdkTracerProvider.builder().addSpanProcessor(BatchSpanProcessor(mainScope, spanExporters)).setResource(
        resource).build()

      otelSdkBuilder.setTracerProvider(tracerProvider)

      ShutDownTracker.getInstance().registerShutdownTask {
        tracerProvider.shutdown().join(10, TimeUnit.SECONDS)
      }
    }
  }

  private fun getDefaultSpanExporters(): List<AsyncSpanExporter> {
    val spanExporters = mutableListOf<AsyncSpanExporter>()
    val traceFile = System.getProperty("idea.diagnostic.opentelemetry.file")
    val traceEndpoint = System.getProperty("idea.diagnostic.opentelemetry.otlp")
    if (traceFile != null) {
      spanExporters.add(JaegerJsonSpanExporter(Path.of(traceFile), serviceName, serviceVersion, serviceNamespace))
    }
    if (traceEndpoint != null) {
      spanExporters.add(OtlpSpanExporter(traceEndpoint))
    }
    return spanExporters
  }

  private fun getDefaultMetricsExporters(): List<MetricExporter> {
    metricsReportingPath?: return emptyList()

    val metricsExporters = mutableListOf<MetricExporter>()
    val writeMetricsTo = MetricsExporterUtils.generateFileForMetrics(metricsReportingPath)
    metricsExporters.add(FilteredMetricsExporter(CsvMetricsExporter(writeMetricsTo)) { metric ->
      !metric.name.contains("lux") && !metric.name.contains("rdct")
    })
    return metricsExporters
  }

  private fun metricsEnabled(): Boolean {
    return metricsReportingPath != null
  }
}