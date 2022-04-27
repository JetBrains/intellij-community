// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

public final class TracerManager {
  public static SpanBuilder spanBuilder(String spanName) {
    return tracer.spanBuilder(spanName);
  }

  static {
    SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
      .addSpanProcessor(BatchSpanProcessor.builder(SpanExporter.composite(TracerProviderManager.spanExporterProvider.get())).build())
      .setResource(Resource.create(Attributes.of(ResourceAttributes.SERVICE_NAME, "builder")))
      .build();

    OpenTelemetrySdk openTelemetry = OpenTelemetrySdk.builder()
      .setTracerProvider(tracerProvider)
      .buildAndRegisterGlobal();

    tracer = openTelemetry.getTracer("build-script");
    TracerProviderManager.tracerProvider = tracerProvider;
  }

  @Nullable
  public static Path finish() {
    return JaegerJsonSpanExporter.finish(TracerProviderManager.getTracerProvider());
  }

  private static final Tracer tracer;
}
