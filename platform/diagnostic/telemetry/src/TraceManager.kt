// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic.telemetry

import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.util.ShutDownTracker
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.metrics.Meter
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.metrics.export.MetricExporter
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * See [Span](https://opentelemetry.io/docs/reference/specification),
 * [Manual Instrumentation](https://opentelemetry.io/docs/instrumentation/java/manual/#create-spans-with-events).
 */
@ApiStatus.Experimental
@ApiStatus.Internal
object TraceManager {
  private var sdk: OpenTelemetry = OpenTelemetry.noop()
  private var verboseSdk: OpenTelemetry = OpenTelemetry.noop()

  fun init(mainScope: CoroutineScope) {
    val traceFile = System.getProperty("idea.diagnostic.opentelemetry.file")
    val endpoint = System.getProperty("idea.diagnostic.opentelemetry.otlp")

    if (traceFile == null && endpoint == null) {
      // noop
      return
    }

    val serviceName = ApplicationNamesInfo.getInstance().fullProductName
    val appInfo = ApplicationInfoImpl.getShadowInstance()
    val serviceVersion = appInfo.build.asStringWithoutProductCode()
    val serviceNamespace = appInfo.build.productCode

    val spanExporters = mutableListOf<AsyncSpanExporter>()
    val metricExporters = mutableListOf<MetricExporter>()
    if (traceFile != null) {
      spanExporters.add(JaegerJsonSpanExporter(file = Path.of(traceFile),
                                               serviceName = serviceName,
                                               serviceVersion = serviceVersion,
                                               serviceNamespace = serviceNamespace))
      metricExporters.add(CsvMetricsExporter(deriveMetricsFile(traceFile)))
    }

    if (endpoint != null) {
      spanExporters.add(OtlpSpanExporter(endpoint))
    }

    val resource = Resource.create(Attributes.of(
      ResourceAttributes.SERVICE_NAME, serviceName,
      ResourceAttributes.SERVICE_VERSION, serviceVersion,
      ResourceAttributes.SERVICE_NAMESPACE, serviceNamespace,
      ResourceAttributes.SERVICE_INSTANCE_ID, DateTimeFormatter.ISO_INSTANT.format(Instant.now()),
    ))

    val otelSdkBuilder = OpenTelemetrySdk.builder()

    if (spanExporters.isNotEmpty()) {
      val tracerProvider = SdkTracerProvider.builder()
        .addSpanProcessor(BatchSpanProcessor(mainScope = mainScope, spanExporters = spanExporters))
        .setResource(resource)
        .build()

      otelSdkBuilder.setTracerProvider(tracerProvider)

      ShutDownTracker.getInstance().registerShutdownTask {
        tracerProvider.shutdown().join(10, TimeUnit.SECONDS)
      }
    }

    if (metricExporters.isNotEmpty()) {
      // no SpanExporter.composite() analog available
      assert(metricExporters.size == 1) {
        "Only single MetricsExporter supported so far, but got: $metricExporters"
      }
      val metricsReader = PeriodicMetricReader.builder(metricExporters.first())
        .setInterval(Duration.ofMinutes(1)) // == default value, but to be explicit
        .build()

      val meterProvider = SdkMeterProvider.builder()
        .registerMetricReader(metricsReader)
        .setResource(resource)
        .build()

      otelSdkBuilder.setMeterProvider(meterProvider)

      ShutDownTracker.getInstance().registerShutdownTask(meterProvider::shutdown)
    }

    sdk = otelSdkBuilder.buildAndRegisterGlobal()

    val useVerboseSdk = System.getProperty("idea.diagnostic.opentelemetry.verbose")
    if (useVerboseSdk?.toBooleanStrictOrNull() == true) {
      verboseSdk = sdk
    }
  }

  private fun deriveMetricsFile(traceFile: String): String {
    val sessionLocalDateTime = LocalDateTime.ofInstant(Instant.now(), ZoneId.systemDefault())
    val dateTime = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss.SSS").format(sessionLocalDateTime)
    return (if (traceFile.endsWith(".json")) traceFile.replace(Regex(".json$"), "") else traceFile) + ".metrics.$dateTime.csv"
  }

  /**
   * We do not provide default tracer - we enforce using of separate scopes for subsystems.
   */
  @JvmOverloads
  fun getTracer(scopeName: String, verbose: Boolean = false): Tracer = (if (verbose) verboseSdk else sdk).getTracer(scopeName)

  fun getMeter(scopeName: String): Meter = sdk.getMeter(scopeName)
}