// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry.rt.context;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.HashMap;
import java.util.StringJoiner;

@ApiStatus.Internal
public final class TelemetryContext extends HashMap<String, String> implements Serializable {

  public static final String TRACE_CONTEXT_JVM_PROPERTY_NAME = "otel.trace.context";

  public @NotNull String asString() {
    StringJoiner joiner = new StringJoiner(", ");
    for (Entry<String, String> entry : entrySet()) {
      joiner.add(entry.getKey() + "=" + entry.getValue());
    }
    return joiner.toString();
  }

  public @NotNull Context extract(@NotNull TextMapPropagator propagator) {
    return propagator.extract(Context.current(), this, new TelemetryContextGetter());
  }

  public @NotNull Context extract() {
    return extract(GlobalOpenTelemetry.getPropagators().getTextMapPropagator());
  }

  public static @NotNull TelemetryContext from(@NotNull Context context, @NotNull TextMapPropagator propagator) {
    TelemetryContext holder = new TelemetryContext();
    propagator.inject(context, holder, new TelemetryContextSetter());
    return holder;
  }

  public static @NotNull TelemetryContext current() {
    return from(Context.current(), GlobalOpenTelemetry.getPropagators().getTextMapPropagator());
  }

  public static @NotNull TelemetryContext fromString(@NotNull String string) {
    TelemetryContext context = new TelemetryContext();
    try {
      String[] particles = string.split(", ");
      for (String tuple : particles) {
        String[] pairs = tuple.split("=");
        if (pairs.length == 2) {
          context.put(pairs[0], pairs[1]);
        }
      }
    }
    catch (Exception ignore) {
      // ignore
    }
    return context;
  }

  public static @NotNull TelemetryContext fromJvmProperties() {
    String value = System.getProperty(TRACE_CONTEXT_JVM_PROPERTY_NAME);
    if (value == null) {
      return new TelemetryContext();
    }
    return fromString(value);
  }
}
