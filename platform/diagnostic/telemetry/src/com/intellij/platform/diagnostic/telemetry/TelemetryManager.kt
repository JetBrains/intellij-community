// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry

import com.intellij.openapi.diagnostic.logger
import com.intellij.util.concurrency.SynchronizedClearableLazy
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.metrics.Meter
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.*
import kotlin.coroutines.CoroutineContext

interface IntelliJTracer {
  fun createSpan(name: String): CoroutineContext
}

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

    fun getSimpleTracer(scope: Scope): IntelliJTracer = instance.value.getSimpleTracer(scope)

    fun getMeter(scope: Scope): Meter = instance.value.getMeter(scope)

    fun setTelemetryManager(value: TelemetryManager) {
      require(!instance.isInitialized())
      instance.value = value
    }

    fun setNoopTelemetryManager() {
      if (!instance.isInitialized()) {
        instance.value = NoopTelemetryManager()
      }
    }
  }

  var verboseMode: Boolean

  /**
   * Method creates a tracer with the scope name.
   * Separate tracers define different scopes, and as a result, separate main nodes in the result data.
   * It is expected that for different subsystems different tracers would be used to isolate the results.
   */
  fun getTracer(scope: Scope): IJTracer

  fun getSimpleTracer(scope: Scope): IntelliJTracer

  fun getMeter(scope: Scope): Meter

  fun addSpansExporters(exporters: List<AsyncSpanExporter>)

  fun addMetricsExporters(exporters: List<MetricsExporterEntry>)
}

private val instance = SynchronizedClearableLazy {
  val log = logger<TelemetryManager>()
  // GlobalOpenTelemetry.set(sdk) can be invoked only once
  val instance = try {
    val aClass = TelemetryManager::class.java
    val implementations = ServiceLoader.load(aClass, aClass.classLoader).toList()
    if (implementations.isEmpty()) {
      log.info("TelemetryManager is not set explicitly and service is not specified - NOOP implementation will be used")
      NoopTelemetryManager()
    }
    else if (implementations.size > 1) {
      log.error("More than one implementation for ${aClass.simpleName} found: ${implementations.map { it::class.qualifiedName }}")
      NoopTelemetryManager()
    }
    else {
      implementations.single()
    }
  }
  catch (e: Throwable) {
    log.info("Cannot create TelemetryManager, falling back to NOOP implementation", e)
    NoopTelemetryManager()
  }

  log.info("Loaded telemetry tracer service ${instance::class.java.name}")
  instance
}

internal class NoopTelemetryManager : TelemetryManager {
  override var verboseMode: Boolean = false

  override fun getTracer(scope: Scope): IJTracer = IJNoopTracer

  override fun getSimpleTracer(scope: Scope) = NoopIntelliJTracer

  override fun getMeter(scope: Scope): Meter = OpenTelemetry.noop().getMeter(scope.toString())

  override fun addSpansExporters(exporters: List<AsyncSpanExporter>) {
  }

  override fun addMetricsExporters(exporters: List<MetricsExporterEntry>) {
  }
}