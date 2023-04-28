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

  fun init(mainScope: CoroutineScope, appInfo: ApplicationInfo, enableMetricsByDefault: Boolean) {
    val otelSdkBuilder = OpenTelemetrySdk.builder()
    sdk = OTelConfigurator(mainScope, otelSdkBuilder, appInfo, enableMetricsByDefault)
      .getConfiguredSdkBuilder()
      .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
      .buildAndRegisterGlobal()

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
   */
  @JvmOverloads
  fun getTracer(scopeName: String, verbose: Boolean = false): IJTracer {
    return wrapTracer(scopeName, sdk.getTracer(scopeName), verbose, verboseMode)
  }

  fun getMeter(scopeName: String): Meter = sdk.getMeter(scopeName)
}