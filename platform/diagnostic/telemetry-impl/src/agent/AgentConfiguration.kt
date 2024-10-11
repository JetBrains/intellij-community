// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry.impl.agent

import com.intellij.platform.diagnostic.telemetry.rt.context.TelemetryContext
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import java.util.*
import kotlin.time.Duration

@ApiStatus.Internal
data class AgentConfiguration(
  val serviceName: String,
  val traceEndpoint: String,
  val settings: Settings,
  val context: TelemetryContext,
  val agentLocation: Path,
) {
  data class Settings(
    val enabled: Boolean,
    val openTelemetryApiEnabled: Boolean,
    val rmiEnabled: Boolean,
    val debug: Boolean,
    val commonMetricsEnabled: Boolean,
    val metricExportInterval: Duration,
  ) {
    companion object {
      @JvmStatic
      fun default(debug: Boolean, metricExportInterval: Duration): Settings {
        return Settings(
          enabled = true,
          openTelemetryApiEnabled = true,
          rmiEnabled = true,
          debug = debug,
          commonMetricsEnabled = true,
          metricExportInterval = metricExportInterval
        )
      }
    }
  }

  companion object {
    @JvmStatic
    fun forService(
      serviceName: String,
      context: TelemetryContext,
      traceEndpoint: String,
      agentLocation: Path,
      settings: Settings,
    ): AgentConfiguration {
      return AgentConfiguration(
        serviceName = serviceName,
        traceEndpoint = traceEndpoint,
        settings = settings,
        context = context,
        agentLocation = agentLocation
      )
    }
  }

  fun toJavaAgentSettings(): Properties {
    return Properties().apply {
      put("otel.traces.exporter", "otlp")
      put("otel.exporter.otlp.endpoint", traceEndpoint)

      put("otel.service.name", serviceName)
      put("otel.metric.export.interval", settings.metricExportInterval.inWholeMilliseconds.toString())

      if (settings.enabled) {
        put("otel.javaagent.enabled", "true")
      }
      if (settings.debug) {
        put("otel.javaagent.debug", "true")
      }
      if (settings.openTelemetryApiEnabled) {
        put("otel.instrumentation.opentelemetry-api.enabled", "true")
      }
      if (settings.rmiEnabled) {
        put("otel.instrumentation.rmi.enabled", "true")
      }
      if (settings.commonMetricsEnabled) {
        put("otel.instrumentation.common.default.enabled", "true")
        put("otel.instrumentation.runtime.metrics.enabled", "true")
        put("otel.instrumentation.jvm.metrics.enabled", "true")
        put("otel.instrumentation.executors.enabled", "true")
        put("otel.instrumentation.jmx-metrics.enabled", "true")
      }
    }
  }
}
