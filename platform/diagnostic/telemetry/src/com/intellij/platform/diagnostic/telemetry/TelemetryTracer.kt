// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry

import com.intellij.openapi.diagnostic.Logger
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.metrics.Meter
import io.opentelemetry.sdk.OpenTelemetrySdkBuilder
import org.jetbrains.annotations.ApiStatus
import java.util.*

private val LOG = Logger.getInstance(TelemetryTracer::class.java)

/**
 * See [Span](https://opentelemetry.io/docs/reference/specification),
 * [Manual Instrumentation](https://opentelemetry.io/docs/instrumentation/java/manual/#create-spans-with-events).
 */
@ApiStatus.Experimental
@ApiStatus.Internal
interface TelemetryTracer {
  var sdk: OpenTelemetry
  var verboseMode: Boolean
  var oTelConfigurator: OpenTelemetryDefaultConfigurator

  fun init(): OpenTelemetrySdkBuilder

  /**
   * Method creates a tracer with the scope name.
   * Separate tracers define different scopes, and as result separate main nodes in the result data.
   * It is expected that for different subsystems different tracers would be used, to isolate the results.
   *
   * @param verbose provides a way to disable by default some tracers.
   *    Such tracers will be created only if additional system property "verbose" is set to true.
   *
   */
  @ApiStatus.Obsolete
  fun getTracer(scopeName: String, verbose: Boolean = false): IJTracer

  fun getTracer(scope: Scope, verbose: Boolean = false): IJTracer = scope.tracer(verbose)

  /**
   * Java shortcut for getTracer(Scope, Boolean)
   */
  fun getTracer(scope: Scope) = getTracer(scope, false)

  /**
   * This function is now obsolete. Please use type-safe alternative getMeter(Scope)
   */
  @ApiStatus.Obsolete
  fun getMeter(scopeName: String): Meter = sdk.getMeter(scopeName)

  fun addSpansExporters(vararg exporters: AsyncSpanExporter) {
    oTelConfigurator.let {
      val aggregatedSpansProcessor = it.aggregatedSpansProcessor
      aggregatedSpansProcessor.addSpansExporters(*exporters)
    }
  }

  fun addMetricsExporters(vararg exporters: MetricsExporterEntry) {
    oTelConfigurator.let {
      val aggregatedMetricsExporter = it.aggregatedMetricsExporter
      aggregatedMetricsExporter.addMetricsExporters(*exporters)
    }
  }

  companion object {
    private lateinit var instance: TelemetryTracer
    private val lock = Any()

    private fun <T> getImplementationService(serviceClass: Class<T>): T {
      val implementations: List<T> = ServiceLoader.load(serviceClass, serviceClass.classLoader).toList()
      if (implementations.isEmpty()) {
        throw ServiceConfigurationError("Implementation for $serviceClass not found")
      }

      if (implementations.size > 1) {
        throw ServiceConfigurationError(
          "More than one implementation for $serviceClass found: ${implementations.map { it!!::class.qualifiedName }}")
      }

      return implementations.single()
    }

    @JvmStatic
    fun getInstance(): TelemetryTracer {
      if (Companion::instance.isInitialized) return instance

      synchronized(lock) {
        if (Companion::instance.isInitialized) return instance

        LOG.info("Initializing TelemetryTracer ...")

        // GlobalOpenTelemetry.set(sdk) can be invoked only once
        try {
          instance = getImplementationService(TelemetryTracer::class.java).apply {
            LOG.info("Loaded telemetry tracer service ${this::class.java.name}")
            sdk = init().buildAndRegisterGlobal()
          }
          return instance
        }
        catch (e: Throwable) {
          LOG.info("Something unexpected happened during loading TelemetryTracer", e)
          LOG.info("Falling back to loading default implementation of TelemetryTracer ${TelemetryTracerDefault::class.java.name}")

          instance = TelemetryTracerDefault().apply {
            LOG.info("Loaded telemetry tracer service ${this::class.java.name}")
            sdk = init().buildAndRegisterGlobal()
          }

          return instance
        }
      }
    }

    @JvmStatic
    fun getMeter(scope: Scope): Meter = scope.meter()
  }
}