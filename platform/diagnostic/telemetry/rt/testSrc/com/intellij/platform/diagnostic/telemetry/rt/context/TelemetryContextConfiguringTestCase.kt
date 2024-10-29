// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry.rt.context

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.sdk.OpenTelemetrySdk
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach

abstract class TelemetryContextConfiguringTestCase {

  companion object {
    @BeforeAll
    @JvmStatic
    fun beforeAll() {
      configureGlobalTelemetry()
    }

    @AfterAll
    @JvmStatic
    fun afterAll() {
      shutdownGlobalTelemetry()
    }

    private fun configureGlobalTelemetry() {
      OpenTelemetrySdk.builder()
        .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
        .buildAndRegisterGlobal()
    }

    private fun shutdownGlobalTelemetry() {
      GlobalOpenTelemetry.resetForTest()
    }
  }

  @BeforeEach
  fun beforeEach() {
    assertCurrentContextIsEmpty()
  }

  private fun assertCurrentContextIsEmpty() {
    val currentContext = Span.current().spanContext
    assertEquals(SpanContext.getInvalid(), currentContext)
  }
}
