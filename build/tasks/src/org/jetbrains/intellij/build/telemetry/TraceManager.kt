// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("LiftReturnOrAssignment")

package org.jetbrains.intellij.build.telemetry

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.platform.buildScripts.concurrency.awaitUninterruptibly
import com.intellij.platform.diagnostic.telemetry.OtlpConfiguration.getTraceEndpoint
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.api.trace.TracerProvider
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SpanExporter
import org.jetbrains.intellij.build.dependencies.BuildDependenciesDownloader
import java.lang.System.Logger
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

/** The longest wait for the shutdown of one exporter. */
private val exporterShutdownTimeout: Duration = 30.seconds

// don't use TraceFileSpanExporter - not needed for clients, should be enabled only if needed to avoid writing a ~500KB JSON file
fun <T> withTracer(serviceName: String, traceFile: Path? = null, block: () -> T): T {
  @Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")
  val exporters: List<SpanExporter> = if (traceFile == null) {
    java.util.List.of(ConsoleSpanExporter())
  }
  else {
    java.util.List.of(ConsoleSpanExporter(), TraceFileSpanExporter(file = traceFile, serviceName = serviceName))
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
internal fun <T> withSpanProcessor(exporters: List<SpanExporter>, block: (BuildSpanProcessor) -> T): T {
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

  /** The tracer of the current build, for an entry point that hands it to a module that cannot depend on this one. */
  fun currentTracer(): Tracer = tracer

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
  private val logger: Logger = System.getLogger(JaegerJsonSpanExporterManager::class.java.name)
  private val shutdownHookAdded = AtomicBoolean()
  private val traceFileSpanExporter = AtomicReference<TraceFileSpanExporter?>()

  internal val spanExporterProvider: List<SpanExporter> by lazy {
    buildList {
      add(ConsoleSpanExporter())
      // the trace file changes with `setOutput`, so the processor holds this delegate and never the file exporter
      add(object : SpanExporter {
        override fun export(spans: Collection<SpanData>): CompletableResultCode {
          return traceFileSpanExporter.get()?.export(spans) ?: CompletableResultCode.ofSuccess()
        }

        override fun flush(): CompletableResultCode {
          return traceFileSpanExporter.get()?.flush() ?: CompletableResultCode.ofSuccess()
        }

        override fun shutdown(): CompletableResultCode {
          return traceFileSpanExporter.getAndSet(null)?.shutdown() ?: CompletableResultCode.ofSuccess()
        }
      })
      val otlpEndPoint = getTraceEndpoint()
      if (otlpEndPoint != null) {
        createOtlpExporter(otlpEndPoint)?.let(::add)
      }
    }
  }

  /** The sender comes from `ServiceLoader`, so a missing sender library is a warning and no OTLP export. */
  private fun createOtlpExporter(endpoint: String): SpanExporter? {
    try {
      return OtlpHttpSpanExporter.builder()
        .setEndpoint(endpoint)
        .setConnectTimeout(10.seconds.toJavaDuration())
        .setTimeout(30.seconds.toJavaDuration())
        .build()
    }
    catch (e: Throwable) {
      rethrowControlFlowException(e)
      logger.log(Logger.Level.WARNING, "Failed to create the OTLP span exporter for $endpoint", e)
      return null
    }
  }

  /** Closes the current trace file. The span processor stays alive, and a later span goes to no file. */
  fun closeOutput() {
    shutdownExporter(traceFileSpanExporter.getAndSet(null))
  }

  fun setOutput(file: Path, addShutDownHook: Boolean = true) {
    shutdownExporter(traceFileSpanExporter.getAndSet(TraceFileSpanExporter(file = file, serviceName = "build")))
    if (addShutDownHook && shutdownHookAdded.compareAndSet(false, true)) {
      Runtime.getRuntime().addShutdownHook(Thread({ TraceManager.shutdown() }, "close tracer"))
    }
  }

  private fun shutdownExporter(exporter: TraceFileSpanExporter?) {
    if (exporter == null) {
      return
    }
    runTelemetryCleanup {
      val result = exporter.shutdown().join(exporterShutdownTimeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
      if (!result.isSuccess) {
        logger.log(Logger.Level.WARNING, "Failed to close the trace file", result.failureThrowable)
      }
    }
  }
}
