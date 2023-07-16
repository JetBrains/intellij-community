// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry.impl

import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.diagnostic.telemetry.*
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.metrics.Meter
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.sdk.OpenTelemetrySdk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.jetbrains.annotations.ApiStatus

/**
 * See [Span](https://opentelemetry.io/docs/reference/specification),
 * [Manual Instrumentation](https://opentelemetry.io/docs/instrumentation/java/manual/#create-spans-with-events).
 */
@ApiStatus.Experimental
internal class TelemetryManagerImpl : TelemetryManager {
  private var sdk: OpenTelemetry = OpenTelemetry.noop()

  override var verboseMode: Boolean = false

  override var oTelConfigurator: OpenTelemetryDefaultConfigurator = OTelConfigurator(mainScope = CoroutineScope(Dispatchers.Default),
                                                                                     otelSdkBuilder = OpenTelemetrySdk.builder(),
                                                                                     appInfo = ApplicationInfoImpl.getShadowInstance(),
                                                                                     enableMetricsByDefault = true)

  override fun getMeter(scope: Scope): Meter = sdk.getMeter(scope.toString())

  override fun init() {
    logger<TelemetryManagerImpl>().info("Initializing telemetry tracer ${this::class.java.name}")

    verboseMode = System.getProperty("idea.diagnostic.opentelemetry.verbose")?.toBooleanStrictOrNull() == true
    sdk = oTelConfigurator
      .getConfiguredSdkBuilder()
      .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
      .buildAndRegisterGlobal()
  }

  override fun getTracer(scope: Scope): IJTracer {
    val name = scope.toString()
    return wrapTracer(scopeName = name, tracer = sdk.getTracer(name), verbose = scope.verbose, verboseMode = verboseMode)
  }
}
