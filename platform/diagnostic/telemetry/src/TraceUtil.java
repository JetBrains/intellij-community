// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic.telemetry;

import com.intellij.openapi.util.ThrowableNotNullFunction;
import com.intellij.util.ThrowableConsumer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import org.jetbrains.annotations.NotNull;


@SuppressWarnings("RedundantThrows") //Kotlin doesn't support checked exception, so we need to create inter-op java / kotlin
public class TraceUtil {

  @NotNull
  public static <T, E extends Throwable> T computeWithSpanThrows(@NotNull Tracer tracer,
                                                                 @NotNull String spanName,
                                                                 @NotNull ThrowableNotNullFunction<Span, T, E> operation) throws E {
    return TraceKt.computeWithSpanIgnoreThrows(tracer, spanName, operation);
  }

  public static <E extends Throwable> void runWithSpanThrows(@NotNull Tracer tracer,
                                                             @NotNull String spanName,
                                                             @NotNull ThrowableConsumer<Span, E> operation) throws E {
    TraceKt.runWithSpanIgnoreThrows(tracer, spanName, operation);
  }
}
