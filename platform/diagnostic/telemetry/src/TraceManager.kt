// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic.telemetry

import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.util.ShutDownTracker
import com.intellij.util.SystemProperties
import com.intellij.openapi.util.io.FileSetLimiter
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.metrics.Meter
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
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * See [Span](https://opentelemetry.io/docs/reference/specification),
 * [Manual Instrumentation](https://opentelemetry.io/docs/instrumentation/java/manual/#create-spans-with-events).
 *
 * TODO Rename TraceManager to more generic (OTel|Telemetry|Monitoring|...)Manager.
 *             Name TraceManager is misleading now: today it is entry-point not only for Traces (spans), but also for Metrics. It looks
 *             unnatural (to me) to request Meter instances with the call like TraceManager.getMeter("meterName").
 *             We could move .getMeter() to another facade (e.g. MeterManager), but this looks artificial (to me:), since Tracer and
 *             Meter are both parts of OpenTelemetry sdk anyway, and inherently configured/initialized together.
 *
 */
@ApiStatus.Experimental
@ApiStatus.Internal
object TraceManager {
  private var sdk: OpenTelemetry = OpenTelemetry.noop()
  private var verboseMode: Boolean = false

  fun init(mainScope: CoroutineScope) {
    val traceFile = System.getProperty("idea.diagnostic.opentelemetry.file")
    val traceEndpoint = System.getProperty("idea.diagnostic.opentelemetry.otlp")
    //RC: Contrary to traces, metrics ARE enabled by default.
    //    Default metrics files look like '<logs>/open-telemetry-metrics.2022-11-01-20-15-44.csv'
    //    To disable metrics: set `-Didea.diagnostic.opentelemetry.metrics.file=""` (i.e. empty string)
    val metricsReportingPath = System.getProperty("idea.diagnostic.opentelemetry.metrics.file",
                                                  "open-telemetry-metrics.csv")

    val metricsEnabled = !metricsReportingPath.isNullOrEmpty()
    if (traceFile == null && traceEndpoint == null && !metricsEnabled) {
      // noop
      return
    }

    val serviceName = ApplicationNamesInfo.getInstance().fullProductName
    val appInfo = ApplicationInfoImpl.getShadowInstance()
    val serviceVersion = appInfo.build.asStringWithoutProductCode()
    val serviceNamespace = appInfo.build.productCode

    val spanExporters = mutableListOf<AsyncSpanExporter>()
    if (traceFile != null) {
      spanExporters.add(JaegerJsonSpanExporter(file = Path.of(traceFile),
                                               serviceName = serviceName,
                                               serviceVersion = serviceVersion,
                                               serviceNamespace = serviceNamespace))
    }
    if (traceEndpoint != null) {
      spanExporters.add(OtlpSpanExporter(traceEndpoint))
    }

    val metricExporters = mutableListOf<MetricExporter>()
    if (metricsEnabled) {
      //if metrics path is relative -> resolve it against IDEA logDir:
      val pathResolvedAgainstLogDir = PathManager.getLogDir().resolve(metricsReportingPath).toAbsolutePath()
      val suffixDateFormat = System.getProperty("idea.diagnostic.opentelemetry.metrics.suffix-date-format", "yyyy-MM-dd-HH-mm-ss")
      val maxFilesToKeep = SystemProperties.getIntProperty("idea.diagnostic.opentelemetry.metrics.max-files-to-keep", 14)

      val writeMetricsTo = FileSetLimiter.inDirectory(pathResolvedAgainstLogDir.parent)
        .withBaseNameAndDateFormatSuffix(pathResolvedAgainstLogDir.fileName.toString(), suffixDateFormat)
        .removeOldFilesBut(maxFilesToKeep)
        .createNewFile()

      metricExporters.add(
        CsvMetricsExporter(writeMetricsTo)
      )
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
      // no analog of SpanExporter.composite() available for metrics:
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
    verboseMode = useVerboseSdk?.toBooleanStrictOrNull() == true
  }

  /**
   * Method creates a tracer with the scope name.
   * Separate tracers define different scopes, and as result separate main nodes in the result data.
   * It is expected that for different subsystems different tracers would be used, to isolate the results.
   *
   * @param verbose provides a way to disable by default some tracers.
   *    Such tracers will be created only if additional system property "verbose" is set to true.
   *
   */
  @JvmOverloads
  fun getTracer(scopeName: String, verbose: Boolean = false): IJTracer {
    return wrapTracer(scopeName, sdk.getTracer(scopeName), verbose, verboseMode)
  }

  fun noopTracer(): IJTracer {
    return IJNoopTracer
  }

  fun getMeter(scopeName: String): Meter = sdk.getMeter(scopeName)
}