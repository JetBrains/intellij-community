// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.intellij.diagnostic.telemetry.JaegerJsonSpanExporter
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import io.opentelemetry.sdk.trace.export.SpanExporter
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Supplier

internal val tracer: Tracer by lazy { TraceManager.tracer }

object TraceManager {
  internal val tracer: Tracer

  init {
    val tracerProvider = SdkTracerProvider.builder()
      .addSpanProcessor(BatchSpanProcessor.builder(SpanExporter.composite(TracerProviderManager.spanExporterProvider.get())).build())
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
    TracerProviderManager.tracerProvider?.forceFlush()?.join(10, TimeUnit.SECONDS)
    return JaegerJsonSpanExporter.finish()
  }
}

object TracerProviderManager {
  private val shutdownHookAdded = AtomicBoolean()

  var tracerProvider: SdkTracerProvider? = null

  var spanExporterProvider: Supplier<List<SpanExporter>> = Supplier { listOf(ConsoleSpanExporter(), JaegerJsonSpanExporter()) }

  fun setOutput(file: Path) {
    JaegerJsonSpanExporter.setOutput(file, serviceName = "build")

    if (shutdownHookAdded.compareAndSet(false, true)) {
      Runtime.getRuntime().addShutdownHook(Thread({
                                                    tracerProvider?.let {
                                                      tracerProvider = null

                                                      it.forceFlush()?.join(10, TimeUnit.SECONDS)
                                                      JaegerJsonSpanExporter.finish()
                                                      it.shutdown().join(10, TimeUnit.SECONDS)
                                                    }
                                                  }, "close tracer"))
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