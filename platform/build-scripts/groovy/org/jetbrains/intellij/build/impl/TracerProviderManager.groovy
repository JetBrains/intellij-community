// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import groovy.transform.CompileStatic
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SpanExporter
import org.jetbrains.annotations.NotNull

import java.util.concurrent.TimeUnit
import java.util.function.Supplier

@CompileStatic
final class TracerProviderManager {
  static SdkTracerProvider tracerProvider

  static @NotNull Supplier<List<SpanExporter>> spanExporterProvider = new Supplier<List<SpanExporter>>() {
    @Override
    List<SpanExporter> get() {
      return List.<SpanExporter> of((SpanExporter)new ConsoleSpanExporter(), (SpanExporter)new JaegerJsonSpanExporter())
    }
  }

  static void flush() {
    try {
      tracerProvider?.forceFlush()?.join(5, TimeUnit.SECONDS)
    }
    catch (Throwable ignored) {
    }
  }
}