// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry.impl

import com.intellij.platform.diagnostic.telemetry.IJTracer
import com.intellij.platform.diagnostic.telemetry.OpenTelemetryDefaultConfigurator
import com.intellij.platform.diagnostic.telemetry.TelemetryTracer
import com.intellij.platform.diagnostic.telemetry.wrapTracer
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.diagnostic.Logger
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.metrics.Meter
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.OpenTelemetrySdkBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.jetbrains.annotations.ApiStatus

private val LOG = Logger.getInstance(TraceManager::class.java)

/**
 * See [Span](https://opentelemetry.io/docs/reference/specification),
 * [Manual Instrumentation](https://opentelemetry.io/docs/instrumentation/java/manual/#create-spans-with-events).
 *
 * TODO Rename TraceManager to more generic (OTel|Telemetry|Monitoring|...)Manager
 *             Name TraceManager is misleading now: today it is entry-point not only for Traces (spans), but also for Metrics. It looks
 *             unnatural (to me) to request Meter instances with the call like TelemetryTracer.getMeter("meterName").
 *             We could move .getMeter() to another facade (e.g. MeterManager), but this looks artificial (to me:), since Tracer and
 *             Meter are both parts of OpenTelemetry sdk anyway, and inherently configured/initialized together.
 *
 */
@ApiStatus.Experimental
@ApiStatus.Internal
class TraceManager : TelemetryTracer {
  override var sdk: OpenTelemetry = OpenTelemetry.noop()
  override var verboseMode: Boolean = false
  override var oTelConfigurator: OpenTelemetryDefaultConfigurator = OTelConfigurator(mainScope = CoroutineScope(Dispatchers.Default),
                                                                                     otelSdkBuilder = OpenTelemetrySdk.builder(),
                                                                                     appInfo = ApplicationInfoImpl.getShadowInstance(),
                                                                                     enableMetricsByDefault = true)

  override fun init(): OpenTelemetrySdkBuilder {
    LOG.info("Initializing telemetry tracer ${this::class.qualifiedName}")

    verboseMode = System.getProperty("idea.diagnostic.opentelemetry.verbose")?.toBooleanStrictOrNull() == true
    return oTelConfigurator
      .getConfiguredSdkBuilder()
      .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
  }

  override fun getTracer(scopeName: String, verbose: Boolean): IJTracer {
    return wrapTracer(scopeName, sdk.getTracer(scopeName), verbose, verboseMode)
  }

  override fun getMeter(scopeName: String): Meter = sdk.getMeter(scopeName)
}