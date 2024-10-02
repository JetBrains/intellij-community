// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry.rt.context

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.context.Context
import io.opentelemetry.context.propagation.TextMapGetter
import io.opentelemetry.context.propagation.TextMapSetter
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class TelemetryContext : HashMap<String, String>() {
  companion object {
    @JvmStatic
    fun current(): TelemetryContext {
      val context = TelemetryContext()
      GlobalOpenTelemetry.get()
        .propagators
        .textMapPropagator
        .inject(Context.current(), context, TelemetryContextSetter())
      return context
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
  }

  fun asString(): String {
    return entries.joinToString(", ")
  }

  fun extract(): Context {
    return GlobalOpenTelemetry.get()
      .propagators
      .textMapPropagator
      .extract(Context.current(), this, TelemetryContextGetter())
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