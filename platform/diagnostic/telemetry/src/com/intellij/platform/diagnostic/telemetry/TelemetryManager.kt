// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry

import com.intellij.openapi.diagnostic.logger
import com.intellij.util.concurrency.SynchronizedClearableLazy
import io.opentelemetry.api.metrics.Meter
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.*

/**
 * See [Span](https://opentelemetry.io/docs/reference/specification),
 * [Manual Instrumentation](https://opentelemetry.io/docs/instrumentation/java/manual/#create-spans-with-events).
 */
@ApiStatus.Experimental
@Internal
interface TelemetryManager {
  companion object {
    @JvmStatic
    fun getInstance(): TelemetryManager = instance.value

    fun getTracer(scope: Scope): IJTracer = instance.value.getTracer(scope)

    fun getMeter(scope: Scope): Meter = instance.value.getMeter(scope)
  }

  var verboseMode: Boolean

  var oTelConfigurator: OpenTelemetryDefaultConfigurator

  fun init()

  /**
   * Method creates a tracer with the scope name.
   * Separate tracers define different scopes, and as a result, separate main nodes in the result data.
   * It is expected that for different subsystems different tracers would be used to isolate the results.
   *
   * @param verbose provides a way to disable by default some tracers.
   *    Such tracers will be created only if additional system property "verbose" is set to true.
   */
  @ApiStatus.Obsolete
  fun getTracer(scopeName: String, verbose: Boolean = false): IJTracer

  fun getTracer(scope: Scope): IJTracer = getTracer(scopeName = scope.toString(), verbose = scope.verbose)

  fun getMeter(scope: Scope): Meter

  fun addSpansExporters(vararg exporters: AsyncSpanExporter) {
    oTelConfigurator.aggregatedSpansProcessor.addSpansExporters(*exporters)
  }

  fun addMetricsExporters(vararg exporters: MetricsExporterEntry) {
    oTelConfigurator.aggregatedMetricsExporter.addMetricsExporters(*exporters)
  }
}

private val instance = SynchronizedClearableLazy {
  val log = logger<TelemetryManager>()
  log.info("Initializing TelemetryTracer ...")

  // GlobalOpenTelemetry.set(sdk) can be invoked only once
  val instance = try {
    getImplementationService(TelemetryManager::class.java)
  }
  catch (e: Throwable) {
    log.info("Something unexpected happened during loading TelemetryTracer", e)
    log.info("Falling back to loading default implementation of TelemetryTracer ${TelemetryDefaultManager::class.java.name}")
    TelemetryDefaultManager()
  }

  log.info("Loaded telemetry tracer service ${instance::class.java.name}")
  instance.init()
  instance
}

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