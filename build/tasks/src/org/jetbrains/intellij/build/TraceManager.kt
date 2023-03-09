// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.intellij.diagnostic.telemetry.AsyncSpanExporter
import com.intellij.diagnostic.telemetry.BatchSpanProcessor
import com.intellij.diagnostic.telemetry.JaegerJsonSpanExporter
import com.intellij.diagnostic.telemetry.OtlpSpanExporter
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes
import kotlinx.coroutines.GlobalScope
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal val tracer: Tracer by lazy { TraceManager.tracer }

object TraceManager {
  internal val tracer: Tracer

  init {
    @Suppress("OPT_IN_USAGE")
    val tracerProvider = SdkTracerProvider.builder()
      .addSpanProcessor(BatchSpanProcessor(mainScope = GlobalScope, spanExporters = TracerProviderManager.spanExporterProvider()))
      .setResource(Resource.create(Attributes.of(ResourceAttributes.SERVICE_NAME, "builder")))
      .build()
    val openTelemetry = OpenTelemetrySdk.builder()
      .setTracerProvider(tracerProvider)
      .build()
    tracer = openTelemetry.getTracer("build-script")
    TracerProviderManager.tracerProvider = tracerProvider
  }

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

      override fun shutdown() {
        jaegerJsonSpanExporter.getAndSet(null)?.shutdown()
      }
    })
    val endpoint = System.getenv("OTLP_ENDPOINT")
    if (endpoint != null) {
      list.add(OtlpSpanExporter(endpoint))
    }
    list
  }

  fun setOutput(file: Path) {
    Files.createDirectories(file.parent)
    jaegerJsonSpanExporter.getAndSet(JaegerJsonSpanExporter(file, serviceName = "build"))?.shutdown()
    if (shutdownHookAdded.compareAndSet(false, true)) {
      Runtime.getRuntime().addShutdownHook(Thread({
                                                    tracerProvider?.let {
                                                      tracerProvider = null

                                                      it.forceFlush()?.join(10, TimeUnit.SECONDS)
                                                      jaegerJsonSpanExporter.getAndSet(null)?.shutdown()
                                                      it.shutdown().join(10, TimeUnit.SECONDS)
                                                    }
                                                  }, "close tracer"))
    }
  }

  fun finish(): Path? {
    tracerProvider?.forceFlush()?.join(10, TimeUnit.SECONDS)
    return jaegerJsonSpanExporter.getAndSet(null)?.let {
      val file = it.file
      it.shutdown()
      file
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