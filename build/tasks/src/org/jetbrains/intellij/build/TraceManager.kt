// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("LiftReturnOrAssignment")

package org.jetbrains.intellij.build

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.intellij.build.dependencies.BuildDependenciesDownloader
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

var traceManagerInitializer: () -> Pair<Tracer, BatchSpanProcessor> = {
  @Suppress("OPT_IN_USAGE")
  val batchSpanProcessor = BatchSpanProcessor(coroutineScope = GlobalScope, spanExporters = TracerProviderManager.spanExporterProvider())
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

  init {
    val config = traceManagerInitializer()
    tracer = config.first
    batchSpanProcessor = config.second
  }

  fun spanBuilder(spanName: String): SpanBuilder = tracer.spanBuilder(spanName)

  suspend fun flush() {
    batchSpanProcessor.flush()
  }

  suspend fun exportPendingSpans() {
    batchSpanProcessor.doFlush(exportOnly = true)
  }
}

object TracerProviderManager {
  private val shutdownHookAdded = AtomicBoolean()
  private val jaegerJsonSpanExporter = AtomicReference<JaegerJsonSpanExporter?>()

  internal val spanExporterProvider: () -> List<AsyncSpanExporter> = {
    val list = mutableListOf(ConsoleSpanExporter(), object : AsyncSpanExporter {
      override suspend fun export(spans: Collection<SpanData>) {
        jaegerJsonSpanExporter.get()?.export(spans)
      }

      override suspend fun shutdown() {
        jaegerJsonSpanExporter.getAndSet(null)?.shutdown()
      }

      override suspend fun flush() {
        jaegerJsonSpanExporter.getAndSet(null)?.flush()
      }
    })
    normalizeOtlpEndPoint(System.getenv("OTLP_ENDPOINT"))?.let {
      list.add(OtlpSpanExporter(it))
    }
    list
  }

  suspend fun setOutput(file: Path) {
    withContext(Dispatchers.IO) {
      Files.createDirectories(file.parent)
    }
    jaegerJsonSpanExporter.getAndSet(JaegerJsonSpanExporter(file, serviceName = "build"))?.shutdown()
    if (shutdownHookAdded.compareAndSet(false, true)) {
      Runtime.getRuntime().addShutdownHook(Thread({
                                                    jaegerJsonSpanExporter.getAndSet(null)?.let {
                                                      runBlocking {
                                                        TraceManager.flush()
                                                        it.shutdown()
                                                      }
                                                    }
                                                  }, "close tracer"))
    }
  }

  suspend fun finish(): Path? {
    try {
      return jaegerJsonSpanExporter.getAndSet(null)?.let {
        TraceManager.flush()
        val file = it.file
        it.shutdown()
        file
      }
    }
    catch (e: IOException) {
      e.printStackTrace(System.err)
      return null
    }
  }
}