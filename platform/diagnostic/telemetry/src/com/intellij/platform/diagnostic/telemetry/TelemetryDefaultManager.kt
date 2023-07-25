// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry

import com.intellij.openapi.diagnostic.logger
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.metrics.Meter
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.sdk.OpenTelemetrySdk

internal class TelemetryDefaultManager : TelemetryManager {
  private var sdk = OpenTelemetry.noop()

  override var verboseMode: Boolean = false

  override fun addSpansExporters(exporters: List<AsyncSpanExporter>) {
  }

  override fun addMetricsExporters(exporters: List<MetricsExporterEntry>) {
  }

  override fun init() {
    logger<TelemetryDefaultManager>().info("Initializing telemetry tracer ${this::class.qualifiedName}")

    verboseMode = System.getProperty("idea.diagnostic.opentelemetry.verbose")?.toBooleanStrictOrNull() == true
    val configurator = OpenTelemetryDefaultConfigurator(otelSdkBuilder = OpenTelemetrySdk.builder(), enableMetricsByDefault = true)
    sdk = configurator
      .getConfiguredSdkBuilder()
      .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance())).buildAndRegisterGlobal()
  }

  override fun getTracer(scope: Scope): IJTracer {
    val name = scope.toString()
    return wrapTracer(name, sdk.getTracer(name), scope.verbose, verboseMode)
  }

  override fun getSimpleTracer(scope: Scope) = NoopIntelliJTracer

  override fun getMeter(scope: Scope): Meter = sdk.getMeter(scope.toString())
}