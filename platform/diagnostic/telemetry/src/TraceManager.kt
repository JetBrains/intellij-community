// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic.telemetry

import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.ShutDownTracker
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.metrics.Meter
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.exporter.jaeger.JaegerGrpcSpanExporter
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.metrics.export.MetricExporter
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import io.opentelemetry.sdk.trace.export.SpanExporter
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit.SECONDS

/**
 * See [Span](https://opentelemetry.io/docs/reference/specification),
 * [Manual Instrumentation](https://opentelemetry.io/docs/instrumentation/java/manual/#create-spans-with-events).
 */
@ApiStatus.Experimental
object TraceManager {
  private val LOG: Logger = Logger.getInstance(TraceManager::class.java)

  private var sdk: OpenTelemetry = OpenTelemetry.noop()
  private var verboseSdk: OpenTelemetry = OpenTelemetry.noop()

  fun init() {
    val traceFile = System.getProperty("idea.diagnostic.opentelemetry.file")
    val endpoint = System.getProperty("idea.diagnostic.opentelemetry.otlp")
    val jaegerEndpoint = System.getProperty("idea.diagnostic.opentelemetry.jaeger")

    if (traceFile == null && endpoint == null && jaegerEndpoint == null) {
      // noop
      return
    }

    val serviceName = ApplicationNamesInfo.getInstance().fullProductName
    val appInfo = ApplicationInfoImpl.getShadowInstance()
    val serviceVersion = appInfo.build.asStringWithoutProductCode()
    val serviceNamespace = appInfo.build.productCode

    val spanExporters = mutableListOf<SpanExporter>()
    val metricsExporters = mutableListOf<MetricExporter>()
    try {
      if (traceFile != null) {
        val jsonSpanExporter = JaegerJsonSpanExporter()
        JaegerJsonSpanExporter.setOutput(file = Path.of(traceFile),
                                         serviceName = serviceName,
                                         serviceVersion = serviceVersion,
                                         serviceNamespace = serviceNamespace)
        spanExporters.add(jsonSpanExporter)

        val metricsFile = deriveMetricsFile(traceFile)
        metricsExporters.add(CsvMetricsExporter(metricsFile))
      }

      if (jaegerEndpoint != null) {
        spanExporters.add(JaegerGrpcSpanExporter.builder().setEndpoint(jaegerEndpoint).build())
      }

      if (endpoint != null) {
        spanExporters.add(OtlpGrpcSpanExporter.builder().setEndpoint(endpoint).build())
      }

      if (spanExporters.isNotEmpty() || metricsExporters.isNotEmpty()) {
        LOG.info("Initialize OpenTelemetry: ${spanExporters.size} span & ${metricsExporters.size} metrics exporters")
        val resource = Resource.create(Attributes.of(
          ResourceAttributes.SERVICE_NAME, serviceName,
          ResourceAttributes.SERVICE_VERSION, serviceVersion,
          ResourceAttributes.SERVICE_NAMESPACE, serviceNamespace,
          ResourceAttributes.SERVICE_INSTANCE_ID, DateTimeFormatter.ISO_INSTANT.format(Instant.now()),
        ))

        val otelSdkBuilder = OpenTelemetrySdk.builder()

        if (spanExporters.isNotEmpty()) {
          val tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(BatchSpanProcessor.builder(SpanExporter.composite(spanExporters)).build())
            .setResource(resource)
            .build()

          otelSdkBuilder.setTracerProvider(tracerProvider)

          ShutDownTracker.getInstance().registerShutdownTask(Runnable {
            tracerProvider?.forceFlush()?.join(10, SECONDS)
            JaegerJsonSpanExporter.finish()
          })
        }

        if (metricsExporters.isNotEmpty()) {
          assert(metricsExporters.size == 1) { //no SpanExporter.composite() analog available
            "Only single MetricsExporter supported so far, but got: $metricsExporters"
          }
          val metricsReader = PeriodicMetricReader.builder(metricsExporters.first())
            .setInterval(Duration.ofMinutes(1)) // == default value, but to be explicit
            .build()

          val metricsProvider = SdkMeterProvider.builder()
            .registerMetricReader(metricsReader)
            .setResource(resource)
            .build()

          otelSdkBuilder.setMeterProvider(metricsProvider)

          ShutDownTracker.getInstance().registerShutdownTask(Runnable {
            metricsProvider?.forceFlush()?.join(10, SECONDS)
          })
        }

        sdk = otelSdkBuilder.buildAndRegisterGlobal()

        val useVerboseSdk = System.getProperty("idea.diagnostic.opentelemetry.verbose")
        if (useVerboseSdk?.toBooleanStrictOrNull() == true) {
          verboseSdk = sdk
        }
      }
    }
    catch (t: Throwable) {
      LOG.warn("Can't initialize OpenTelemetry: will use default (noop) SDK impl", t)
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