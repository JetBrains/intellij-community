// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry.helpers;

import com.intellij.openapi.util.ThrowableNotNullFunction;
import com.intellij.util.ThrowableConsumer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import kotlin.jvm.functions.Function1;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;


@SuppressWarnings("RedundantThrows") //Kotlin doesn't support checked exception, so we need to create inter-op java / kotlin
@ApiStatus.Internal
public final class TraceUtil {

  /**
   * A workaround for using {@link TraceKt#use(SpanBuilder, Function1)} with checked exceptions.
   * Prefer using {@link TraceKt#use(SpanBuilder, Function1)} where possible.
   */
  @NotNull
  public static <T, E extends Throwable> T computeWithSpanThrows(@NotNull SpanBuilder spanBuilder,
                                                                 @NotNull ThrowableNotNullFunction<Span, T, E> operation) throws E {
    return TraceKt.computeWithSpanIgnoreThrows(spanBuilder, operation);
  }

  /**
   * A workaround for using {@link TraceKt#use(SpanBuilder, Function1)} with checked exceptions.
   * Prefer using {@link TraceKt#use(SpanBuilder, Function1)} where possible.
   */
  public static <E extends Throwable> void runWithSpanThrows(@NotNull SpanBuilder spanBuilder,
                                                             @NotNull ThrowableConsumer<Span, E> operation) throws E {
    TraceKt.runWithSpanIgnoreThrows(spanBuilder, operation);
  }
}
