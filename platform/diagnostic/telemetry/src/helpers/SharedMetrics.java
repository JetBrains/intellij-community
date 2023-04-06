// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic.telemetry.helpers;

import com.intellij.diagnostic.telemetry.IJTracer;
import com.intellij.diagnostic.telemetry.TraceManager;
import com.intellij.diagnostic.telemetry.TracerLevel;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.context.Context;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public abstract class SharedMetrics {

  public final String rootScopeName;
  public final IJTracer tracer;

  public SharedMetrics(String scopeName) {
    rootScopeName = scopeName;
    this.tracer = TraceManager.INSTANCE.getTracer(rootScopeName);
  }

  private final ConcurrentHashMap<String, Span> spans = new ConcurrentHashMap<>();

  public Context getSpanContext(String spanName) {
    return Context.current().with(getOrStartSpan(spanName));
  }

  public Span getOrStartSpan(@NotNull String spanName) {
    return getOrStartSpan(spanName, (builder) -> builder);
  }

  /**
   * Create span if it doesn't exist. Otherwise, return already present span.
   */
  public Span getOrStartSpan(@NotNull String spanName, @NotNull String parentSpanName) {
    getOrStartSpan(parentSpanName);
    return getOrStartSpan(spanName, (builder) -> builder.setParent(getSpanContext(parentSpanName)));
  }

  public Span getOrStartSpan(@NotNull String spanName, Function<SpanBuilder, SpanBuilder> action) {
    return spans.computeIfAbsent(spanName, (name) -> action.apply(tracer.spanBuilder(spanName, TracerLevel.DEFAULT)).startSpan());
  }

  /**
   * Will always start a new span under the parent span
   */
  public Span startNewSpan(@NotNull String spanName, @NotNull String parentSpanName) {
    getOrStartSpan(parentSpanName);
    return startNewSpan(spanName, (builder) -> builder.setParent(getSpanContext(parentSpanName)));
  }

  /**
   * Will always start a new span
   */
  public Span startNewSpan(@NotNull String spanName, Function<SpanBuilder, SpanBuilder> action) {
    return spans.put(spanName, action.apply(tracer.spanBuilder(spanName, TracerLevel.DEFAULT)).startSpan());
  }

  public void endSpan(@NotNull String spanName) {
    endSpan(spanName, (span) -> span);
  }

  public void endSpan(@NotNull String spanName, Function<Span, Span> action) {
    if (!spans.containsKey(spanName)) {
      throw new RuntimeException(String.format("Span with name %s isn't started yet, but called to stop", spanName));
    }

    var span = spans.get(spanName);
    if (span != null) {
      action.apply(span).end();
    }
  }

  public Meter getMeter() {
    return TraceManager.INSTANCE.getMeter(rootScopeName);
  }
}
