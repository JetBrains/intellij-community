// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.rpc.core

import fleet.tracing.opentelemetry
import io.opentelemetry.context.Context
import io.opentelemetry.context.propagation.TextMapGetter
import io.opentelemetry.context.propagation.TextMapSetter
import kotlinx.serialization.Serializable

@Serializable
data class TelemetryData(val traceContext: Map<String, String>) {
  companion object {
    internal val serializer = serializer()

    val otelGetter: TextMapGetter<TelemetryData> = object : TextMapGetter<TelemetryData> {
      override fun keys(carrier: TelemetryData): Iterable<String> = carrier.traceContext.keys

      override fun get(carrier: TelemetryData?, key: String): String? = carrier?.traceContext?.get(key)
    }

    internal val otelSetter: TextMapSetter<MutableMap<String, String>> =
      TextMapSetter<MutableMap<String, String>> { carrier, key, value ->
        carrier?.set(key, value)
      }
  }
}

internal fun Context.toTelemetryData(): TelemetryData {
  val map = HashMap<String, String>()
  opentelemetry.propagators.textMapPropagator.inject(this, map, TelemetryData.otelSetter)
  return TelemetryData(map)
}
