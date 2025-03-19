// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry.helpers;

import com.intellij.platform.diagnostic.telemetry.IJTracer;
import com.intellij.platform.diagnostic.telemetry.Scope;
import com.intellij.platform.diagnostic.telemetry.TelemetryManager;
import com.intellij.platform.diagnostic.telemetry.TracerLevel;
import com.intellij.openapi.diagnostic.Logger;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.context.Context;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@ApiStatus.Internal
public abstract class SharedMetrics {
  public final Scope rootScopeName;
  public final IJTracer tracer;

  public SharedMetrics(@NotNull Scope scope) {
    rootScopeName = scope;
    this.tracer = TelemetryManager.Companion.getTracer(scope);
  }

  private final Map<String, Span> spans = new ConcurrentHashMap<>();
  private static final Logger LOG = Logger.getInstance(SharedMetrics.class);

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
      LOG.error(String.format("Span with name %s isn't started yet, but was called to stop", spanName));
    }

    Span span = spans.get(spanName);
    if (span != null) {
      try {
        action.apply(span).end();
      }
      catch (Exception e) {
        LOG.error(String.format("Error while stopping span %s ", spanName), e);
      }
    }
  }

  public Meter getMeter() {
    return TelemetryManager.getInstance().getMeter(rootScopeName);
  }
}
