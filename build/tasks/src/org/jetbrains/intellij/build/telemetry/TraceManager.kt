// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("LiftReturnOrAssignment")

package org.jetbrains.intellij.build.telemetry

import com.intellij.platform.diagnostic.telemetry.AsyncSpanExporter
import com.intellij.platform.diagnostic.telemetry.OtlpConfiguration.getTraceEndpoint
import com.intellij.platform.diagnostic.telemetry.exporters.BatchSpanProcessor
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
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.plus
import kotlinx.coroutines.runBlocking
import org.jetbrains.intellij.build.dependencies.BuildDependenciesDownloader
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.seconds

// don't use JaegerJsonSpanExporter - not needed for clients, should be enabled only if needed to avoid writing a ~500KB JSON file
fun withTracer(serviceName: String, traceFile: Path? = null, block: suspend () -> Unit): Unit = runBlocking(Dispatchers.Default) {
  val batchSpanProcessorScope = CoroutineScope(SupervisorJob(parent = coroutineContext.job)) + CoroutineName("BatchSpanProcessor")

  @Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")
  val spanProcessor = BatchSpanProcessor(
    coroutineScope = batchSpanProcessorScope,
    spanExporters = if (traceFile == null) {
      java.util.List.of(ConsoleSpanExporter())
    }
    else {
      java.util.List.of(ConsoleSpanExporter(), JaegerJsonSpanExporter(file = traceFile, serviceName = serviceName))
    },
    scheduleDelay = 10.seconds,
  )
  try {
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
  finally {
    batchSpanProcessorScope.cancel()
    traceManagerInitializer = { throw IllegalStateException("already built") }
  }
}

suspend fun withoutTracer(block: suspend () -> Unit) {
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

private var traceManagerInitializer: () -> Pair<Tracer, BatchSpanProcessor?> = {
  val batchSpanProcessor = BatchSpanProcessor(
    scheduleDelay = 10.seconds,
    coroutineScope = CoroutineScope(Job()),
    spanExporters = JaegerJsonSpanExporterManager.spanExporterProvider,
  )
  val tracerProvider = SdkTracerProvider.builder()
    .addSpanProcessor(batchSpanProcessor)
    .setResource(Resource.create(Attributes.of(AttributeKey.stringKey("service.name"), "builder")))
    .build()
  val openTelemetry = OpenTelemetrySdk.builder()
    .setTracerProvider(tracerProvider)
    .build()
  val tracer = openTelemetry.getTracer("build-script")
  BuildDependenciesDownloader.TRACER = tracer
  tracer to batchSpanProcessor
}

object TraceManager {
  private val tracerLock = Any()
  private val tracerOverrideStack = ArrayDeque<TracerOverrideHandle>()

  @Volatile
  private var tracer: Tracer
  private val batchSpanProcessor: BatchSpanProcessor?
  private val isEnabled = System.getProperty("intellij.build.export.opentelemetry.spans")?.toBoolean() ?: false

  init {
    val config = traceManagerInitializer()
    tracer = config.first
    batchSpanProcessor = config.second
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

  suspend fun flush() {
    batchSpanProcessor?.flush()
  }

  suspend fun shutdown() {
    batchSpanProcessor?.forceShutdown()
  }

  suspend fun scheduleExportPendingSpans() {
    if (isEnabled) {
      batchSpanProcessor?.scheduleFlush()
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

  suspend fun setOutput(file: Path, addShutDownHook: Boolean = true) {
    jaegerJsonSpanExporter.getAndSet(JaegerJsonSpanExporter(file = file, serviceName = "build"))?.shutdown()
    if (addShutDownHook && shutdownHookAdded.compareAndSet(false, true)) {
      Runtime.getRuntime().addShutdownHook(
        Thread({
                 runBlocking(Dispatchers.IO) {
                   TraceManager.shutdown()
                 }
               }, "close tracer"))
    }
  }
}