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
import kotlinx.coroutines.GlobalScope
import org.jetbrains.intellij.build.dependencies.BuildDependenciesDownloader
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

var traceManagerInitializer: () -> Tracer = {
  @Suppress("OPT_IN_USAGE")
  val tracerProvider = SdkTracerProvider.builder()
    .addSpanProcessor(BatchSpanProcessor(coroutineScope = GlobalScope, spanExporters = TracerProviderManager.spanExporterProvider()))
    .setResource(Resource.create(Attributes.of(AttributeKey.stringKey("service.name"), "builder")))
    .build()
  val openTelemetry = OpenTelemetrySdk.builder()
    .setTracerProvider(tracerProvider)
    .build()
  val tracer = openTelemetry.getTracer("build-script")
  TracerProviderManager.tracerProvider = tracerProvider
  BuildDependenciesDownloader.TRACER = tracer
  tracer
}

object TraceManager {
  private val tracer: Tracer = traceManagerInitializer()

  @JvmStatic
  fun spanBuilder(spanName: String): SpanBuilder = tracer.spanBuilder(spanName)

  fun finish(): Path? {
    return TracerProviderManager.finish()
  }
}

object TracerProviderManager {
  private val shutdownHookAdded = AtomicBoolean()
  private val jaegerJsonSpanExporter = AtomicReference<JaegerJsonSpanExporter?>()

  var tracerProvider: SdkTracerProvider? = null

  var spanExporterProvider: () -> List<AsyncSpanExporter> = {
    val list = mutableListOf(ConsoleSpanExporter(), object : AsyncSpanExporter {
      override suspend fun export(spans: Collection<SpanData>) {
        jaegerJsonSpanExporter.get()?.export(spans)
      }

      override suspend fun shutdown() {
        jaegerJsonSpanExporter.getAndSet(null)?.shutdown()
      }
    })
    normalizeOtlpEndPoint(System.getenv("OTLP_ENDPOINT"))?.let {
      list.add(OtlpSpanExporter(it))
    }
    list
  }

  fun setOutput(file: Path) {
    Files.createDirectories(file.parent)
    jaegerJsonSpanExporter.getAndSet(JaegerJsonSpanExporter(file, serviceName = "build"))?.shutdownSync()
    if (shutdownHookAdded.compareAndSet(false, true)) {
      Runtime.getRuntime().addShutdownHook(Thread({
                                                    tracerProvider?.let {
                                                      tracerProvider = null

                                                      it.forceFlush()?.join(10, TimeUnit.SECONDS)
                                                      jaegerJsonSpanExporter.getAndSet(null)?.shutdownSync()
                                                      it.shutdown().join(10, TimeUnit.SECONDS)
                                                    }
                                                  }, "close tracer"))
    }
  }

  fun finish(): Path? {
    try {
      tracerProvider?.forceFlush()?.join(10, TimeUnit.SECONDS)
      return jaegerJsonSpanExporter.getAndSet(null)?.let {
        val file = it.file
        it.shutdownSync()
        file
      }
    }
    catch (e: IOException) {
      e.printStackTrace(System.err)
      return null
    }
  }

  fun flush() {
    try {
      tracerProvider?.forceFlush()?.join(5, TimeUnit.SECONDS)
    }
    catch (ignored: Throwable) {
    }
  }
}