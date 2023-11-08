// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry.impl

import com.intellij.diagnostic.ActivityImpl
import com.intellij.diagnostic.PluginException
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.Ref
import com.intellij.platform.diagnostic.telemetry.*
import com.intellij.platform.diagnostic.telemetry.exporters.BatchSpanProcessor
import com.intellij.platform.diagnostic.telemetry.exporters.JaegerJsonSpanExporter
import com.intellij.platform.diagnostic.telemetry.exporters.OtlpSpanExporter
import com.intellij.util.childScope
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.metrics.Meter
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.data.SpanData
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext

/**
 * See [Span](https://opentelemetry.io/docs/reference/specification),
 * [Manual Instrumentation](https://opentelemetry.io/docs/instrumentation/java/manual/#create-spans-with-events).
 */
@ApiStatus.Experimental
@ApiStatus.Internal
class TelemetryManagerImpl(coroutineScope: CoroutineScope, isUnitTestMode: Boolean) : TelemetryManager {
  // for the unit (performance) tests
  @Suppress("unused", "DEPRECATION")
  constructor() : this(ApplicationManager.getApplication().coroutineScope, ApplicationManager.getApplication().isUnitTestMode)

  private val sdk: OpenTelemetrySdk

  override var verboseMode: Boolean = false

  private val aggregatedMetricExporter: AggregatedMetricExporter
  private val hasSpanExporters: Boolean

  private val otlpService: OtlpService

  init {
    verboseMode = System.getProperty("idea.diagnostic.opentelemetry.verbose")?.toBooleanStrictOrNull() == true
    val configurator = createOpenTelemetryConfigurator(appInfo = ApplicationInfoImpl.getShadowInstance())
    aggregatedMetricExporter = configurator.aggregatedMetricExporter
    otlpService = OtlpService()

    var otlJob: Job? = null
    val spanExporters = createSpanExporters(configurator.resource, isUnitTestMode = isUnitTestMode)
    hasSpanExporters = !spanExporters.isEmpty()
    var otlpServiceCoroutineScope = coroutineScope
    val batchSpanProcessor = if (hasSpanExporters) {
      spanExporters.add(object : AsyncSpanExporter {
        override suspend fun export(spans: Collection<SpanData>) {
        }

        override suspend fun shutdown() {
          otlpService.stop()
          otlJob?.let {
            otlJob = null
            it.join()
          }
        }
      })

      val batchSpanProcessor = BatchSpanProcessor(coroutineScope = coroutineScope,
                                                  spanExporters = java.util.List.copyOf(spanExporters))
      // make sure that otlpService job is canceled before BatchSpanProcessor job
      otlpServiceCoroutineScope = coroutineScope.childScope(supervisor = false)
      val tracerProvider = SdkTracerProvider.builder()
        .addSpanProcessor(batchSpanProcessor)
        .setResource(configurator.resource)
        .build()
      configurator.sdkBuilder.setTracerProvider(tracerProvider)
      batchSpanProcessor
    }
    else {
      null
    }

    otlJob = otlpService.process(coroutineScope = otlpServiceCoroutineScope,
                                 batchSpanProcessor = batchSpanProcessor,
                                 opentelemetrySdkResource = configurator.resource)

    // W3CTraceContextPropagator is needed to make backend/client spans properly synced, issue: RDCT-408
    sdk = configurator.getConfiguredSdkBuilder()
      .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
      .buildAndRegisterGlobal()
  }

  fun addStartupActivities(activities: List<ActivityImpl>) {
    val scope = Scope("startup")
    for (activity in activities) {
      if (activity.scope == null) {
        activity.scope = scope
      }
      otlpService.add(activity)
    }
  }

  override fun addMetricsExporters(exporters: List<MetricsExporterEntry>) {
    aggregatedMetricExporter.addMetricsExporters(exporters)
  }

  override fun getMeter(scope: Scope): Meter = sdk.getMeter(scope.toString())

  override fun getTracer(scope: Scope): IJTracer {
    if (!hasSpanExporters) {
      return IJNoopTracer
    }

    val name = scope.toString()
    return wrapTracer(scopeName = name, tracer = sdk.getTracer(name), verbose = scope.verbose, verboseMode = verboseMode)
  }

  override fun getSimpleTracer(scope: Scope): IntelliJTracer {
    return if (hasSpanExporters) IntelliJTracerImpl(scope = scope, otlpService = otlpService) else NoopIntelliJTracer
  }

  override fun forceFlushMetrics() {
    logger<TelemetryManagerImpl>().info("Forcing flushing OpenTelemetry metrics ...")

    listOf(
      sdk.sdkMeterProvider.forceFlush(),
      sdk.sdkTracerProvider.forceFlush()
    ).forEach { it.join(10, TimeUnit.SECONDS) }

    aggregatedMetricExporter.flush().join(10, TimeUnit.SECONDS)

    logger<TelemetryManagerImpl>().info("OpenTelemetry metrics were flushed")
  }
}

private class IntelliJTracerImpl(private val scope: Scope, private val otlpService: OtlpService) : IntelliJTracer {
  private val traceReporter = object : DefaultTraceReporter(reportScheduleTimeForRoot = true) {
    override fun setEndAndAdd(activity: ActivityImpl, end: Long) {
      activity.setEnd(end)
      activity.scope = scope
      otlpService.add(activity)
    }
  }

  override suspend fun span(name: String): CoroutineContext {
    return createSpan(traceReporter = traceReporter) + CoroutineName(name)
  }

  override suspend fun span(name: String, attributes: Array<String>): CoroutineContext {
    return CoroutineName(name) +
           createSpan(traceReporter = createAttributeAwareReporter(name, Ref(attributes.takeIf { it.isNotEmpty() })))
  }

  override fun rootSpan(name: String, attributes: Array<String>): CoroutineContext {
    return CoroutineName(name) +
           createRootSpan(traceReporter = createAttributeAwareReporter(name, Ref(attributes.takeIf { it.isNotEmpty() })))
  }

  private fun createAttributeAwareReporter(name: String, ref: Ref<Array<String>>): DefaultTraceReporter {
    return object : DefaultTraceReporter(reportScheduleTimeForRoot = true) {
      override fun setEndAndAdd(activity: ActivityImpl, end: Long) {
        // yes, compare by identity
        if (activity.name === name) {
          activity.attributes = ref.get()
          ref.set(null)
        }

        traceReporter.setEndAndAdd(activity, end)
      }
    }
  }
}

private fun createSpanExporters(resource: Resource, isUnitTestMode: Boolean = false): MutableList<AsyncSpanExporter> {
  val spanExporters = mutableListOf<AsyncSpanExporter>()
  System.getProperty("idea.diagnostic.opentelemetry.file")?.let { traceFile ->
    spanExporters.add(JaegerJsonSpanExporter(
      file = Path.of(traceFile),
      serviceName = resource.getAttribute(AttributeKey.stringKey("service.name"))!!,
      serviceVersion = resource.getAttribute(AttributeKey.stringKey("service.version")),
      serviceNamespace = resource.getAttribute(AttributeKey.stringKey("service.namespace")),
    ))
  }

  getOtlpEndPoint()?.let {
    spanExporters.add(OtlpSpanExporter(it))
  }

  // Extension points for "com.intellij.openTelemetryExporterProvider" isn't available in unit tests
  if (isUnitTestMode) {
    return spanExporters
  }

  for (item in ExtensionPointName<OpenTelemetryExporterProvider>("com.intellij.openTelemetryExporterProvider").filterableLazySequence()) {
    val pluginDescriptor = item.pluginDescriptor
    if (!pluginDescriptor.isBundled) {
      logger<OpenTelemetryExporterProvider>().error(PluginException("Plugin ${pluginDescriptor.pluginId} is not allowed " +
                                                                    "to provide OpenTelemetryExporterProvider", pluginDescriptor.pluginId))
      continue
    }

    spanExporters.addAll((item.instance ?: continue).getSpanExporters())
  }
  return spanExporters
}

private fun createOpenTelemetryConfigurator(appInfo: ApplicationInfo): OpenTelemetryConfigurator {
  return OpenTelemetryConfigurator(
    sdkBuilder = OpenTelemetrySdk.builder(),
    serviceName = ApplicationNamesInfo.getInstance().fullProductName,
    serviceVersion = appInfo.build.asStringWithoutProductCode(),
    serviceNamespace = appInfo.build.productCode,
    enableMetricsByDefault = true,
    customResourceBuilder = {
      // don't write username to file - it maybe private information
      if (getOtlpEndPoint() != null) {
        it.put(AttributeKey.stringKey("process.owner"), System.getProperty("user.name") ?: "unknown")
      }
    },
  )
}