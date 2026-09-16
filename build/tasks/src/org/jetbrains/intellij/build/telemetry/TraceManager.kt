// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("LiftReturnOrAssignment")

package org.jetbrains.intellij.build.telemetry

import com.intellij.platform.buildScripts.concurrency.awaitUninterruptibly
import com.intellij.platform.diagnostic.telemetry.AsyncSpanExporter
import com.intellij.platform.diagnostic.telemetry.OtlpConfiguration.getTraceEndpoint
import com.intellij.platform.diagnostic.telemetry.exporters.JaegerJsonSpanExporter
import com.intellij.platform.diagnostic.telemetry.exporters.OtlpSpanExporter
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.api.trace.TracerProvider
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.data.SpanData
import kotlinx.coroutines.runBlocking
import org.jetbrains.intellij.build.dependencies.BuildDependenciesDownloader
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.seconds

// don't use JaegerJsonSpanExporter - not needed for clients, should be enabled only if needed to avoid writing a ~500KB JSON file
fun <T> withTracer(serviceName: String, traceFile: Path? = null, block: () -> T): T {
  @Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")
  val exporters = if (traceFile == null) {
    java.util.List.of(ConsoleSpanExporter())
  }
  else {
    java.util.List.of(ConsoleSpanExporter(), JaegerJsonSpanExporter(file = traceFile, serviceName = serviceName))
  }
  try {
    return withSpanProcessor(exporters) { spanProcessor ->
      val tracerProvider = SdkTracerProvider.builder()
        .addSpanProcessor(spanProcessor)
        .setResource(Resource.create(Attributes.of(AttributeKey.stringKey("service.name"), serviceName)))
        .build()

      traceManagerInitializer = {
        val openTelemetry = OpenTelemetrySdk.builder()
          .setTracerProvider(tracerProvider)
          .build()
        val tracer = openTelemetry.getTracer("build-script")
        BuildDependenciesDownloader.TRACER = tracer
        tracer to spanProcessor
      }
      block()
    }
  }
  finally {
    traceManagerInitializer = { throw IllegalStateException("already built") }
  }
}

/** Runs [block] with a span processor of its own, and closes the processor when the block returns. */
internal fun <T> withSpanProcessor(exporters: List<AsyncSpanExporter>, block: (BuildSpanProcessor) -> T): T {
  return BuildSpanProcessor(spanExporters = exporters, scheduleDelay = 10.seconds).use(block)
}

/**
 * Runs [action] on a virtual thread of its own and waits for it despite caller interruptions.
 *
 * The caller keeps its interrupt status, and the failure of the action is rethrown. An exporter shutdown must run
 * to the end, or the trace file stays incomplete.
 */
internal fun runTelemetryCleanup(action: () -> Unit) {
  val completion = CompletableFuture<Unit>()
  Thread.ofVirtual().name("build telemetry cleanup").start {
    try {
      action()
      completion.complete(Unit)
    }
    catch (failure: Throwable) {
      completion.completeExceptionally(failure)
    }
  }
  completion.awaitUninterruptibly()
}

fun withoutTracer(block: () -> Unit) {
  try {
    traceManagerInitializer = {
      val tracer = TracerProvider.noop().get("build-script")
      BuildDependenciesDownloader.TRACER = tracer
      tracer to null
    }
    block()
  }
  finally {
    traceManagerInitializer = { throw IllegalStateException("already built") }
  }
}

private var traceManagerInitializer: () -> Pair<Tracer, BuildSpanProcessor?> = {
  val spanProcessor = BuildSpanProcessor(
    scheduleDelay = 10.seconds,
    spanExporters = JaegerJsonSpanExporterManager.spanExporterProvider,
  )
  val tracerProvider = SdkTracerProvider.builder()
    .addSpanProcessor(spanProcessor)
    .setResource(Resource.create(Attributes.of(AttributeKey.stringKey("service.name"), "builder")))
    .build()
  val openTelemetry = OpenTelemetrySdk.builder()
    .setTracerProvider(tracerProvider)
    .build()
  val tracer = openTelemetry.getTracer("build-script")
  BuildDependenciesDownloader.TRACER = tracer
  tracer to spanProcessor
}

object TraceManager {
  private val tracerLock = Any()
  private val tracerOverrideStack = ArrayDeque<TracerOverrideHandle>()

  @Volatile
  private var tracer: Tracer
  private val spanProcessor: BuildSpanProcessor?
  private val isEnabled = System.getProperty("intellij.build.export.opentelemetry.spans")?.toBoolean() ?: false

  init {
    val config = traceManagerInitializer()
    tracer = config.first
    spanProcessor = config.second
  }

  fun setTracer(tracer: Tracer) {
    synchronized(tracerLock) {
      setActiveTracer(tracer)
    }
  }

  fun spanBuilder(spanName: String): SpanBuilder = tracer.spanBuilder(spanName)

  fun pushTracer(tracer: Tracer): AutoCloseable {
    synchronized(tracerLock) {
      val handle = TracerOverrideHandle(previousTracer = this.tracer)
      tracerOverrideStack.addLast(handle)
      setActiveTracer(tracer)
      return handle
    }
  }

  /** Exports the pending spans and blocks until they are out. */
  fun flush() {
    spanProcessor?.flush()
  }

  /** Exports the pending spans, stops the processor and blocks until both are done. */
  fun shutdown() {
    spanProcessor?.close()
  }

  fun scheduleExportPendingSpans() {
    if (isEnabled) {
      spanProcessor?.scheduleFlush()
    }
  }

  private fun setActiveTracer(tracer: Tracer) {
    this.tracer = tracer
    BuildDependenciesDownloader.TRACER = tracer
  }

  private class TracerOverrideHandle(
    private val previousTracer: Tracer,
  ) : AutoCloseable {
    private val isClosed = AtomicBoolean()

    override fun close() {
      if (!isClosed.compareAndSet(false, true)) {
        return
      }

      synchronized(tracerLock) {
        check(tracerOverrideStack.isNotEmpty() && tracerOverrideStack.last() === this) {
          "TraceManager tracer overrides must be closed in LIFO order"
        }
        tracerOverrideStack.removeLast()
        setActiveTracer(previousTracer)
      }
    }
  }
}

object JaegerJsonSpanExporterManager {
  private val shutdownHookAdded = AtomicBoolean()
  private val jaegerJsonSpanExporter = AtomicReference<JaegerJsonSpanExporter?>()

  internal val spanExporterProvider: List<AsyncSpanExporter> by lazy {
    buildList {
      add(ConsoleSpanExporter())
      add(object : AsyncSpanExporter {
        override suspend fun export(spans: Collection<SpanData>) {
          jaegerJsonSpanExporter.get()?.export(spans)
        }

        override suspend fun flush() {
          jaegerJsonSpanExporter.get()?.flush()
        }

        override suspend fun shutdown() {
          jaegerJsonSpanExporter.getAndSet(null)?.shutdown()
        }
      })
      val otlpEndPoint = getTraceEndpoint()
      if (otlpEndPoint != null) {
        add(OtlpSpanExporter(otlpEndPoint))
      }
    }
  }

  /** Closes the current trace file. The span processor stays alive, and a later span goes to no file. */
  fun closeOutput() {
    shutdownExporter(jaegerJsonSpanExporter.getAndSet(null))
  }

  fun setOutput(file: Path, addShutDownHook: Boolean = true) {
    shutdownExporter(jaegerJsonSpanExporter.getAndSet(JaegerJsonSpanExporter(file = file, serviceName = "build")))
    if (addShutDownHook && shutdownHookAdded.compareAndSet(false, true)) {
      Runtime.getRuntime().addShutdownHook(Thread({ TraceManager.shutdown() }, "close tracer"))
    }
  }

  private fun shutdownExporter(exporter: JaegerJsonSpanExporter?) {
    if (exporter == null) {
      return
    }
    // the exporter of the platform is a coroutine API, so the shutdown enters coroutines here
    runTelemetryCleanup {
      runBlocking {
        exporter.shutdown()
      }
    }
  }
}
