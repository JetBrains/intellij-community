// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry

import com.intellij.openapi.diagnostic.logger
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.metrics.Meter
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.OpenTelemetrySdkBuilder
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
@ApiStatus.Internal
class TelemetryDefaultManager : TelemetryManager {
  private var sdk: OpenTelemetry = OpenTelemetry.noop()

  override fun setSdk(sdk: OpenTelemetry) {
    this.sdk = sdk
  }

  override var verboseMode: Boolean = false
  override var oTelConfigurator: OpenTelemetryDefaultConfigurator =
    OpenTelemetryDefaultConfigurator(otelSdkBuilder = OpenTelemetrySdk.builder(), enableMetricsByDefault = true)

  override fun init(): OpenTelemetrySdkBuilder {
    logger<TelemetryDefaultManager>().info("Initializing telemetry tracer ${this::class.qualifiedName}")

    verboseMode = System.getProperty("idea.diagnostic.opentelemetry.verbose")?.toBooleanStrictOrNull() == true

    return oTelConfigurator
      .getConfiguredSdkBuilder()
      .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
  }

  override fun getTracer(scopeName: String, verbose: Boolean): IJTracer {
    return wrapTracer(scopeName, sdk.getTracer(scopeName), verbose, verboseMode)
  }

  override fun getMeter(scope: Scope): Meter = sdk.getMeter(scope.toString())
}