// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry.rt.context;

import io.opentelemetry.context.propagation.TextMapGetter;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class TelemetryContextGetter implements TextMapGetter<TelemetryContext> {

  @Override
  public @NotNull Iterable<String> keys(@NotNull TelemetryContext carrier) {
    return carrier.keySet();
  }

  @Override
  public @Nullable String get(@Nullable TelemetryContext carrier, @NotNull String key) {
    if (carrier == null) {
      return null;
    }
    return carrier.get(key);
  }
}
