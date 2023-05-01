// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic.telemetry

import com.intellij.openapi.application.ApplicationInfo
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.metrics.Meter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.ContextPropagators
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Obsolete

/**
 * See [Span](https://opentelemetry.io/docs/reference/specification),
 * [Manual Instrumentation](https://opentelemetry.io/docs/instrumentation/java/manual/#create-spans-with-events).
 *
 * TODO Rename TraceManager to more generic (OTel|Telemetry|Monitoring|...)Manager
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
  private var oTelConfigurator: OTelConfigurator? = null

  fun init(mainScope: CoroutineScope, appInfo: ApplicationInfo, enableMetricsByDefault: Boolean) {
    val otelSdkBuilder = OpenTelemetrySdk.builder()
    oTelConfigurator = OTelConfigurator(mainScope, otelSdkBuilder, appInfo, enableMetricsByDefault)
    oTelConfigurator?.let {
      sdk = it.getConfiguredSdkBuilder().setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
        .buildAndRegisterGlobal()
    }
    verboseMode = System.getProperty("idea.diagnostic.opentelemetry.verbose")?.toBooleanStrictOrNull() == true
  }

  /**
   * Method creates a tracer with the scope name.
   * Separate tracers define different scopes, and as result separate main nodes in the result data.
   * It is expected that for different subsystems different tracers would be used, to isolate the results.
   *
   * @param verbose provides a way to disable by default some tracers.
   *    Such tracers will be created only if additional system property "verbose" is set to true.
   *
   * This function is now obsolete. Please use type-safe alternative getTracer(Scope)
   */
  @Obsolete
  @JvmOverloads
  fun getTracer(scopeName: String, verbose: Boolean = false): IJTracer {
    return wrapTracer(scopeName, sdk.getTracer(scopeName), verbose, verboseMode)
  }
  /**
   * This function is now obsolete. Please use type-safe alternative getMeter(Scope)
   */
  @Obsolete
  fun getMeter(scopeName: String): Meter = sdk.getMeter(scopeName)

  @JvmStatic
  fun getMeter(scope: Scope): Meter = scope.meter()

  @JvmStatic
  @JvmOverloads
  fun getTracer(scope: Scope, verbose: Boolean = false): IJTracer = scope.tracer(verbose)

  fun addSpansExporters(vararg exporters: AsyncSpanExporter) {
    oTelConfigurator?.let {
      val aggregatedSpansProcessor = it.aggregatedSpansProcessor
      aggregatedSpansProcessor.addSpansExporters(*exporters)
    }
  }

  fun addMetricsExporters(vararg exporters: MetricsExporterEntry) {
    oTelConfigurator?.let {
      val aggregatedMetricsExporter = it.aggregatedMetricsExporter
      aggregatedMetricsExporter.addMetricsExporters(*exporters)
    }
  }
}