// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.diagnostic;

import com.intellij.diagnostic.telemetry.IJTracer;
import com.intellij.diagnostic.telemetry.TraceManager;
import com.intellij.diagnostic.telemetry.TracerLevel;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.context.Context;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public final class ExternalSystemSyncDiagnostic {
  public static final IJTracer syncTracer = TraceManager.INSTANCE.getTracer("external-system-sync");

  private static final ConcurrentHashMap<String, Span> spans = new ConcurrentHashMap<>();

  public static final String gradleSyncSpanName = "gradle.sync.duration"; // Named that way for legacy metric name compatibility

  public static Context getSpanContext(String spanName) {
    return Context.current().with(getOrStartSpan(spanName));
  }

  public static Span getOrStartSpan(@NotNull String spanName) {
    return getOrStartSpan(spanName, (builder) -> builder);
  }

  public static Span getOrStartSpan(@NotNull String spanName, Function<SpanBuilder, SpanBuilder> action) {
    return spans.computeIfAbsent(spanName, (name) -> action.apply(syncTracer.spanBuilder(spanName, TracerLevel.DEFAULT)).startSpan());
  }

  public static void endSpan(@NotNull String spanName) {
    endSpan(spanName, (span) -> span);
  }

  public static void endSpan(@NotNull String spanName, Function<Span, Span> action) {
    if (!spans.containsKey(spanName)) {
      throw new RuntimeException(String.format("Span with name %s isn't started yet, but called to stop", spanName));
    }

    action.apply(spans.get(spanName)).end();
  }
}
