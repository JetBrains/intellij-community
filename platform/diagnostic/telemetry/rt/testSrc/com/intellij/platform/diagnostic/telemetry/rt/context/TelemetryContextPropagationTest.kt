// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry.rt.context

import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.TraceFlags
import io.opentelemetry.api.trace.TraceState
import io.opentelemetry.context.Context
import io.opentelemetry.context.ContextKey
import io.opentelemetry.context.propagation.TextMapGetter
import io.opentelemetry.context.propagation.TextMapPropagator
import io.opentelemetry.context.propagation.TextMapSetter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class TelemetryContextPropagationTest : TelemetryContextConfiguringTestCase() {

  @Test
  fun `test context injection with custom propagator`() {
    val traceId = "42a7da3d96c8ff6350d304ca56f54242"
    val spanId = "b8da87d1d227aaaa"
    withTestContext(traceId, spanId) {
      val telemetryContext = TelemetryContext.from(Context.current(), object : TextMapPropagator {
        override fun fields(): Collection<String?>? {
          throw IllegalStateException("This method should not be invoked")
        }

        override fun <C : Any?> inject(context: Context, carrier: C?, setter: TextMapSetter<C?>) {
          val sctx = Span.fromContext(context).spanContext
          assertEquals(traceId, sctx.traceId)
          assertEquals(spanId, sctx.spanId)
          setter.set(carrier, "hello", "world");
        }

        override fun <C : Any?> extract(context: Context, carrier: C?, getter: TextMapGetter<C?>): Context? {
          throw IllegalStateException("This method should not be invoked")
        }
      })
      assertEquals(1, telemetryContext.size)
      assertEquals("world", telemetryContext["hello"])
    }
  }

  @Test
  fun `test context extraction with custom propagation`() {
    val expectedContext = object : Context {
      override fun <V : Any?> get(key: ContextKey<V?>): V? {
        throw IllegalStateException("This method should not be invoked")
      }

      override fun <V : Any?> with(k1: ContextKey<V?>, v1: V & Any): Context? {
        throw IllegalStateException("This method should not be invoked")
      }
    }
    val extracted = TelemetryContext.current()
      .extract(object : TextMapPropagator {
        override fun fields(): Collection<String?>? {
          throw IllegalStateException("This method should not be invoked")
        }

        override fun <C : Any?> inject(context: Context, carrier: C?, setter: TextMapSetter<C?>) {
          throw IllegalStateException("This method should not be invoked")
        }

        override fun <C : Any?> extract(context: Context, carrier: C?, getter: TextMapGetter<C?>): Context? {
          assertNotNull(context)
          assertNotNull(carrier)
          assertNotNull(getter)
          return expectedContext
        }
      })
    assertEquals(expectedContext, extracted)
  }

  @Test
  fun `test context propagation from IDEA side`() {
    val traceId = "79a7da3d96c8ff6350d304ca56f56711"
    val spanId = "b8da87d1d227aedc"
    withTestContext(traceId, spanId) {
      val currentContext = TelemetryContext.current()
      val capturedContext = currentContext.asString()
      assertEquals("traceparent=00-$traceId-$spanId-00", capturedContext)
      val recreatedContext = TelemetryContext.fromString(capturedContext)
      assertEquals(currentContext, recreatedContext)
    }
  }

  @Test
  fun `test context propagation onto IDEA side`() {
    val traceId = "42a7da3d96c8ff6350d304ca56f56711"
    val spanId = "b8da87d1d242aedc"
    val context = TelemetryContext.fromString("traceparent=00-$traceId-$spanId-00")
    context.extract()
      .makeCurrent()
      .use { _ ->
        val currentContext = Span.current().spanContext
        assertEquals(traceId, currentContext.traceId)
        assertEquals(spanId, currentContext.spanId)
      }
  }

  @Test
  fun `context deserialization should be fail-safe`() {
    assertNotNull(TelemetryContext.fromString("abc"))
    assertNotNull(TelemetryContext.fromString("abc,"))
    assertNotNull(TelemetryContext.fromString("abc,            , , , ,  "))
  }

  private fun withTestContext(traceId: String, spanId: String, fn: () -> Unit) {
    val ctx = SpanContext.createFromRemoteParent(
      traceId,
      spanId,
      TraceFlags.getDefault(),
      TraceState.getDefault()
    )
    Span.wrap(ctx)
      .makeCurrent()
      .use { _ -> fn() }
  }
}
