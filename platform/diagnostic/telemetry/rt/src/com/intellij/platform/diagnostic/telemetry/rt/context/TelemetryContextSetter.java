// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry.rt.context;

import io.opentelemetry.context.propagation.TextMapSetter;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class TelemetryContextSetter implements TextMapSetter<TelemetryContext> {

  @Override
  public void set(@Nullable TelemetryContext carrier, @NotNull String key, @Nullable String value) {
    if (carrier != null) {
      carrier.put(key, value);
    }
  }
}
