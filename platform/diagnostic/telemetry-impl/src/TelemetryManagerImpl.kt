// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry.impl

import com.intellij.diagnostic.ActivityImpl
import com.intellij.diagnostic.DefaultTraceReporter
import com.intellij.diagnostic.rootTask
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.components.service
import com.intellij.platform.diagnostic.telemetry.*
import com.intellij.util.childScope
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.metrics.Meter
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.OpenTelemetrySdkBuilder
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import kotlin.coroutines.CoroutineContext

/**
 * See [Span](https://opentelemetry.io/docs/reference/specification),
 * [Manual Instrumentation](https://opentelemetry.io/docs/instrumentation/java/manual/#create-spans-with-events).
 */
@ApiStatus.Experimental
@ApiStatus.Internal
class TelemetryManagerImpl(app: Application) : TelemetryManager {
  private val sdk: OpenTelemetry

  private val otlpService by lazy {
    ApplicationManager.getApplication().service<OtlpService>()
  }

  override var verboseMode: Boolean = false

  private val aggregatedMetricExporter: AggregatedMetricExporter
  private val aggregatedSpanProcessor: AggregatedSpanProcessor

  @Volatile
  private var hasExporters: Boolean

  init {
    verboseMode = System.getProperty("idea.diagnostic.opentelemetry.verbose")?.toBooleanStrictOrNull() == true
    @Suppress("DEPRECATION")
    val configurator = createOpenTelemetryConfigurator(mainScope = app.coroutineScope.childScope(),
                                                       otelSdkBuilder = OpenTelemetrySdk.builder(),
                                                       appInfo = ApplicationInfoImpl.getShadowInstance())

    aggregatedMetricExporter = configurator.aggregatedMetricExporter
    aggregatedSpanProcessor = configurator.aggregatedSpanProcessor

    val spanExporters = createSpanExporters(configurator.resource)
    hasExporters = !spanExporters.isEmpty()
    configurator.registerSpanExporters(spanExporters = spanExporters)
    sdk = configurator.getConfiguredSdkBuilder()
      .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
      .buildAndRegisterGlobal()
  }

  override fun addSpansExporters(exporters: List<AsyncSpanExporter>) {
    if (!hasExporters && !exporters.isEmpty()) {
      hasExporters = true
    }
    aggregatedSpanProcessor.addSpansExporters(exporters)
  }

  override fun addMetricsExporters(exporters: List<MetricsExporterEntry>) {
    aggregatedMetricExporter.addMetricsExporters(exporters)
  }

  override fun getMeter(scope: Scope): Meter = sdk.getMeter(scope.toString())

  override fun getTracer(scope: Scope): IJTracer {
    val name = scope.toString()
    return wrapTracer(scopeName = name, tracer = sdk.getTracer(name), verbose = scope.verbose, verboseMode = verboseMode)
  }

  override fun getSimpleTracer(scope: Scope): IntelliJTracer {
    return if (hasExporters) IntelliJTracerImpl(scope, otlpService) else NoopIntelliJTracer
  }
}

private class IntelliJTracerImpl(private val scope: Scope, private val otlpService: OtlpService) : IntelliJTracer {
  override fun createSpan(name: String): CoroutineContext {
    return rootTask(traceReporter = object : DefaultTraceReporter(reportScheduleTimeForRoot = true) {
      override fun setEndAndAdd(activity: ActivityImpl, end: Long) {
        activity.setEnd(end)
        activity.scope = scope
        otlpService.add(activity)
      }
    }) + CoroutineName(name)
  }
}

@Suppress("SuspiciousCollectionReassignment")
private fun createSpanExporters(resource: Resource): List<AsyncSpanExporter> {
  var spanExporters = emptyList<AsyncSpanExporter>()
  System.getProperty(
    "idea.diagnostic.opentelemetry.file")?.let { traceFile ->
    @Suppress("SuspiciousCollectionReassignment")
    spanExporters += JaegerJsonSpanExporter(
      file = Path.of(traceFile),
      serviceName = resource.getAttribute(ResourceAttributes.SERVICE_NAME)!!,
      serviceVersion = resource.getAttribute(ResourceAttributes.SERVICE_VERSION),
      serviceNamespace = resource.getAttribute(ResourceAttributes.SERVICE_NAMESPACE),
    )
  }

  getOtlpEndPoint()?.let {
    spanExporters += OtlpSpanExporter(it)
  }
  return spanExporters
}

private fun createOpenTelemetryConfigurator(mainScope: CoroutineScope,
                                            otelSdkBuilder: OpenTelemetrySdkBuilder,
                                            appInfo: ApplicationInfo): OpenTelemetryConfigurator {
  return OpenTelemetryConfigurator(
    mainScope = mainScope,
    sdkBuilder = otelSdkBuilder,
    serviceName = ApplicationNamesInfo.getInstance().fullProductName,
    serviceVersion = appInfo.build.asStringWithoutProductCode(),
    serviceNamespace = appInfo.build.productCode,
    enableMetricsByDefault = true,
    customResourceBuilder = {
      // don't write username to file - it maybe private information
      if (getOtlpEndPoint() != null) {
        it.put(ResourceAttributes.PROCESS_OWNER, System.getProperty("user.name") ?: "unknown")
      }
    },
  )
}
