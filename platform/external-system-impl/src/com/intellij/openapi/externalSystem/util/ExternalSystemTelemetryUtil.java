// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.util;

import com.intellij.openapi.externalSystem.diagnostic.ExternalSystemObservabilityScopesKt;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.platform.diagnostic.telemetry.TelemetryManager;
import com.intellij.platform.diagnostic.telemetry.helpers.TraceKt;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Function;

@ApiStatus.Internal
public final class ExternalSystemTelemetryUtil {

  public static @NotNull Tracer getTracer(@Nullable ProjectSystemId id) {
    return TelemetryManager.getInstance()
      .getTracer(ExternalSystemObservabilityScopesKt.forSystem(id == null ? ProjectSystemId.IDE : id));
  }

  public static void runWithSpan(@Nullable ProjectSystemId id, @NotNull String spanName, @NotNull Consumer<Span> fn) {
    TraceKt.use(getTracer(id).spanBuilder(spanName), span -> {
      fn.accept(span);
      return null;
    });
  }
}
