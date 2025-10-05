// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("SSBasedInspection")

package com.intellij.platform.diagnostic.telemetry

import com.intellij.openapi.diagnostic.logger
import com.intellij.util.concurrency.SynchronizedClearableLazy
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.metrics.Meter
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
import java.util.*
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * See [Span](https://opentelemetry.io/docs/reference/specification),
 * [Manual Instrumentation](https://opentelemetry.io/docs/instrumentation/java/manual/#create-spans-with-events).
 *
 * Commonly used entry point to work with OpenTelemetry. Configures and initializes OpenTelemetry instances.
 *
 * Using tracer example:
 * ```
 * val myTracer = TelemetryManager.getInstance().getTracer(VcsScopeKt.VcsScope)
 * val span = myTracer.spanBuilder("my.span").startSpan()
 * ... code you want to trace ...
 * span.end()
 * ```
 *
 * Using meter example:
 * ```
 * val jvmMeter = TelemetryManager.getMeter(JVM)
 * val threadCountGauge = jvmMeter.gaugeBuilder("JVM.threadCount").ofLongs().buildObserver()
 * jvmMeter.batchCallback( { threadCountGauge.record(threadMXBean.threadCount.toLong()) }, threadCountGauge)
 * ```
 */
@Experimental
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

    @TestOnly
    fun forceSetTelemetryManager(value: TelemetryManager = NoopTelemetryManager()) {
      instance.value = value
    }

    @TestOnly
    fun resetGlobalSdk() {
      GlobalOpenTelemetry.resetForTest()
    }
  }

  var verboseMode: Boolean

  fun hasSpanExporters(): Boolean

  /**
   * Method creates a tracer with the scope name.
   * Separate tracers define different scopes, and as a result, separate main nodes in the result data.
   * It is expected that for different subsystems different tracers would be used to isolate the results.
   */
  fun getTracer(scope: Scope): IJTracer

  fun getSimpleTracer(scope: Scope): IntelliJTracer

  fun getMeter(scope: Scope): Meter

  fun addMetricsExporters(exporters: List<MetricsExporterEntry>)

  /**
   * Force collection of measurements and metrics flushing to appropriate files (.json for spans and .csv for meters).
   *
   * [Do not use this method in production code. Since it may be blocking.](https://opentelemetry.io/docs/specs/otel/performance/#shutdown-and-explicit-flushing-could-block)
   **/
  @TestOnly
  suspend fun forceFlushMetrics()

  /** Blocking [forceFlushMetrics] function for test purposes. */
  @Suppress("unused")
  @TestOnly
  fun forceFlushMetricsBlocking() {
    runBlocking { forceFlushMetrics() }
  }

  /**
   * Should reset all registered exporters [com.intellij.platform.diagnostic.telemetry.AsyncSpanExporter.reset].
   * Meaning that spans and meters should be exported and discarded, so that resulting artifacts will not contain previously collected data.
   */
  @TestOnly
  suspend fun resetExporters()

  /** Blocking [resetExporters] counterpart for test purposes and for simplicity of use from Java. */
  @Suppress("unused")
  @TestOnly
  fun resetExportersBlocking() {
    runBlocking { resetExporters() }
  }

  @TestOnly
  suspend fun shutdownExporters()

  /** Blocking [resetExporters] counterpart for test purposes and for simplicity of use from Java. */
  @Suppress("unused")
  @TestOnly
  fun shutdownExportersBlocking() {
    runBlocking { shutdownExporters() }
  }
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

@Internal
class NoopTelemetryManager : TelemetryManager {
  override var verboseMode: Boolean = false

  override fun hasSpanExporters(): Boolean = false

  override fun getTracer(scope: Scope): IJTracer = IJNoopTracer

  override fun getSimpleTracer(scope: Scope) = NoopIntelliJTracer

  override fun getMeter(scope: Scope): Meter = OpenTelemetry.noop().getMeter(scope.toString())

  override fun addMetricsExporters(exporters: List<MetricsExporterEntry>) {
    logger<NoopTelemetryManager>().info("Noop telemetry manager is in use. No metrics exporters are defined.")
  }

  override suspend fun forceFlushMetrics() {
    logger<NoopTelemetryManager>().info("Cannot force flushing metrics for Noop telemetry manager")
  }

  override suspend fun resetExporters() {
    logger<NoopTelemetryManager>().info("Cannot clean exported metrics for Noop telemetry manager")
  }

  override suspend fun shutdownExporters() {
    logger<NoopTelemetryManager>().info("Cannot shutdown metric exporters for Noop telemetry manager")
  }
}

// suspend here is required to get parent span from coroutine context; that's why a version without `suspend` is called `rootSpan`.
@Internal
@Experimental
interface IntelliJTracer {
  suspend fun span(name: String): CoroutineContext

  suspend fun span(name: String, attributes: Array<String>): CoroutineContext

  fun rootSpan(name: String, attributes: Array<String>): CoroutineContext
}

@Internal
object NoopIntelliJTracer : IntelliJTracer {
  override suspend fun span(name: String) = CoroutineName(name)

  override suspend fun span(name: String, attributes: Array<String>) = span(name)

  override fun rootSpan(name: String, attributes: Array<String>) = EmptyCoroutineContext
}