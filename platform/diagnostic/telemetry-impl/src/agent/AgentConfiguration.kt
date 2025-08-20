// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry.impl.agent

import com.intellij.ide.plugins.PluginModuleId
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.platform.diagnostic.telemetry.rt.context.TelemetryContext
import org.jetbrains.annotations.ApiStatus
import java.net.URI
import java.nio.file.Path
import java.util.*
import kotlin.time.Duration

@ApiStatus.Internal
data class AgentConfiguration(
  val serviceName: String,
  val traceEndpoint: URI,
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
    val metricExportInterval: Duration?,
    val outputJsonDestination: Path?
  ) {

    class Builder(
      var enabled: Boolean = true,
      var openTelemetryApiEnabled: Boolean = true,
      var rmiEnabled: Boolean = true,
      var debug: Boolean = false,
      var commonMetricsEnabled: Boolean = false,
      var metricExportInterval: Duration? = null,
      var outputJsonDestination: Path? = null
    ) {

      fun build() : Settings{
        return Settings(
          enabled = enabled,
          openTelemetryApiEnabled = openTelemetryApiEnabled,
          rmiEnabled = rmiEnabled,
          debug = debug,
          commonMetricsEnabled = commonMetricsEnabled,
          metricExportInterval = metricExportInterval,
          outputJsonDestination = outputJsonDestination
        )
      }

      fun withCommonJvmMetrics() : Builder {
        commonMetricsEnabled = true
        return this
      }

      fun withDebugLogs(): Builder {
        debug = true
        return this
      }

      fun withTelemetryDumpFile(path: Path): Builder {
        outputJsonDestination = path
        return this
      }
    }

    companion object {
      @JvmStatic
      fun builder(): Builder {
        return Builder()
      }
    }
  }

  companion object {

    private const val AGENT_EXTENSION_MODULE_NAME = "intellij.platform.diagnostic.telemetry.agent.extension"

    @JvmStatic
    fun forService(
      serviceName: String,
      context: TelemetryContext,
      traceEndpoint: URI,
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
      put("otel.exporter.otlp.traces.endpoint", traceEndpoint.toString())

      put("otel.service.name", serviceName)

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
        if (settings.metricExportInterval != null) {
          put("otel.metric.export.interval", settings.metricExportInterval.inWholeMilliseconds.toString())
        }
      }
      if (settings.outputJsonDestination != null) {
        val agentExtensionPath = getAgentExtensionPath()
        if (agentExtensionPath != null) {
          put("otel.traces.exporter.json.file.enabled", "true")
          put("otel.traces.exporter.json.file.location", settings.outputJsonDestination.toString())
          put("otel.javaagent.extensions", agentExtensionPath.toString())
        }
      }
    }
  }

  private fun getAgentExtensionPath(): Path? {
    val moduleDescriptor = PluginManagerCore.getPluginSet().findEnabledModule(PluginModuleId(AGENT_EXTENSION_MODULE_NAME))
                           ?: return null
    return moduleDescriptor.jarFiles?.first {
      it.fileName.toString() == "$AGENT_EXTENSION_MODULE_NAME.jar"
    }
  }
}
