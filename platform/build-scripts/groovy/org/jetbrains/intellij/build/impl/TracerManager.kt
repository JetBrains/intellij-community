// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

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

object TracerManager {
  private val tracer: Tracer

  init {
    val tracerProvider = SdkTracerProvider.builder()
      .addSpanProcessor(BatchSpanProcessor.builder(SpanExporter.composite(TracerProviderManager.getSpanExporterProvider().get())).build())
      .setResource(Resource.create(Attributes.of(ResourceAttributes.SERVICE_NAME, "builder")))
      .build()
    val openTelemetry = OpenTelemetrySdk.builder()
      .setTracerProvider(tracerProvider)
      .buildAndRegisterGlobal()
    tracer = openTelemetry.getTracer("build-script")
    TracerProviderManager.setTracerProvider(tracerProvider)
  }

  @JvmStatic
  fun spanBuilder(spanName: String): SpanBuilder = tracer.spanBuilder(spanName)

  fun finish(): Path? {
    return JaegerJsonSpanExporter.finish(TracerProviderManager.getTracerProvider())
  }
}