// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("LiftReturnOrAssignment")

package org.jetbrains.intellij.build.telemetry

import com.intellij.platform.diagnostic.telemetry.AsyncSpanExporter
import com.intellij.platform.diagnostic.telemetry.exporters.BatchSpanProcessor
import com.intellij.platform.diagnostic.telemetry.exporters.JaegerJsonSpanExporter
import com.intellij.platform.diagnostic.telemetry.exporters.OtlpSpanExporter
import com.intellij.platform.diagnostic.telemetry.exporters.normalizeOtlpEndPoint
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.data.SpanData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.jetbrains.intellij.build.dependencies.BuildDependenciesDownloader
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@Suppress("SSBasedInspection")
var traceManagerInitializer: () -> Pair<Tracer, BatchSpanProcessor> = {
  val batchSpanProcessor = BatchSpanProcessor(coroutineScope = CoroutineScope(Job()), spanExporters = JaegerJsonSpanExporterManager.spanExporterProvider)
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
  private val tracer: Tracer
  private val batchSpanProcessor: BatchSpanProcessor
  private val isEnabled = System.getProperty("intellij.build.export.opentelemetry.spans")?.toBoolean() ?: false

  init {
    val config = traceManagerInitializer()
    tracer = config.first
    batchSpanProcessor = config.second
  }

  fun spanBuilder(spanName: String): SpanBuilder = tracer.spanBuilder(spanName)

  suspend fun flush() {
    batchSpanProcessor.flush()
  }

  suspend fun shutdown() {
    batchSpanProcessor.forceShutdown()
  }

  suspend fun exportPendingSpans() {
    if (isEnabled) {
      batchSpanProcessor.doFlush(exportOnly = true)
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
      val otlpEndPoint = normalizeOtlpEndPoint(System.getenv("OTLP_ENDPOINT"))
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
                 runBlocking {
                   TraceManager.shutdown()
                 }
               }, "close tracer"))
    }
  }
}