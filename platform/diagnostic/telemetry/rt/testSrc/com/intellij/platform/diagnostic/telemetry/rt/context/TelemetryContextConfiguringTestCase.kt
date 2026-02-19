// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry.rt.context

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.sdk.OpenTelemetrySdk
import org.junit.jupiter.api.BeforeEach

abstract class TelemetryContextConfiguringTestCase {

  @BeforeEach
  open fun setUp() {
    GlobalOpenTelemetry.resetForTest()
    configureGlobalTelemetry()
  }

  private fun configureGlobalTelemetry() {
    OpenTelemetrySdk.builder()
      .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
      .buildAndRegisterGlobal()
  }
}
