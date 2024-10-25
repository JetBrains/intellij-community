// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry.rt.context

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.context.Context
import io.opentelemetry.context.propagation.TextMapGetter
import io.opentelemetry.context.propagation.TextMapPropagator
import io.opentelemetry.context.propagation.TextMapSetter
import org.jetbrains.annotations.ApiStatus
import java.io.Serializable

@ApiStatus.Internal
class TelemetryContext : HashMap<String, String>(), Serializable {

  companion object {
    const val TRACE_CONTEXT_JVM_PROPERTY_NAME: String = "otel.trace.context"

    @JvmStatic
    fun from(context: Context, propagator: TextMapPropagator): TelemetryContext {
      val holder = TelemetryContext()
      propagator.inject(context, holder, TelemetryContextSetter())
      return holder
    }

    @JvmStatic
    fun current(): TelemetryContext {
      return from(Context.current(), GlobalOpenTelemetry.getPropagators().textMapPropagator)
    }

    @JvmStatic
    fun fromString(string: String): TelemetryContext {
      val context = TelemetryContext()
      try {
        val entries = string.split(", ")
        entries.forEach { tuple ->
          val pairs = tuple.split("=")
          if (pairs.size == 2) {
            context.put(pairs[0], pairs[1])
          }
        }
      }
      catch (e: Exception) {
        // ignore
      }
      return context
    }

    @JvmStatic
    fun fromJvmProperties(): TelemetryContext {
      val value = System.getProperty(TRACE_CONTEXT_JVM_PROPERTY_NAME) ?: return TelemetryContext()
      return fromString(value)
    }
  }

  fun asString(): String {
    return entries.joinToString(", ")
  }

  fun extract(propagator: TextMapPropagator): Context {
    return propagator.extract(Context.current(), this, TelemetryContextGetter())
  }

  fun extract(): Context {
    return extract(GlobalOpenTelemetry.get().propagators.textMapPropagator)
  }
}

private class TelemetryContextGetter : TextMapGetter<TelemetryContext> {
  override fun keys(carrier: TelemetryContext): Iterable<String?> {
    return carrier.keys
  }

  override fun get(carrier: TelemetryContext?, key: String): String? {
    if (carrier == null) {
      return null
    }
    return carrier[key]
  }
}

private class TelemetryContextSetter : TextMapSetter<TelemetryContext> {
  override fun set(carrier: TelemetryContext?, key: String, value: String) {
    if (carrier == null) {
      return
    }
    carrier.put(key, value)
  }
}