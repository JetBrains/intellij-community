// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic.opentelemetry

import com.intellij.diagnostic.telemetry.JaegerJsonSpanExporter
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.util.ShutDownTracker
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import io.opentelemetry.sdk.trace.export.SpanExporter
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * See [Span](https://opentelemetry.io/docs/reference/specification).
 */
@ApiStatus.Experimental
object TraceManager {
  private val sdk: OpenTelemetrySdk

  init {
    val traceFile = System.getProperty("idea.diagnostic.opentelemetry.file")
    val spanExporter: SpanExporter
    var isActive = true
    if (traceFile == null) {
      spanExporter = SpanExporter.composite(emptyList())
      isActive = false
    }
    else {
      val jsonSpanExporter = JaegerJsonSpanExporter()
      JaegerJsonSpanExporter.setOutput(Path.of(traceFile))
      spanExporter = jsonSpanExporter
    }

    val tracerProvider = SdkTracerProvider.builder()
      .addSpanProcessor(BatchSpanProcessor.builder(spanExporter).build())
      .setResource(Resource.create(Attributes.of(ResourceAttributes.SERVICE_NAME, ApplicationNamesInfo.getInstance().fullProductName)))
      .setResource(Resource.create(Attributes.of(ResourceAttributes.SERVICE_VERSION, ApplicationInfo.getInstance().build.asStringWithoutProductCodeAndSnapshot())))
      .build()
    sdk = OpenTelemetrySdk.builder()
      .setTracerProvider(tracerProvider)
      .buildAndRegisterGlobal()

    if (isActive) {
      ShutDownTracker.getInstance().registerShutdownTask(Runnable {
        tracerProvider?.forceFlush()?.join(5, TimeUnit.SECONDS)
      })
    }
  }

  /**
   * We do not provide default tracer - we enforce using of separate scopes for subsystems.
   */
  fun getTracer(scopeName: String): Tracer = sdk.getTracer(scopeName)
}