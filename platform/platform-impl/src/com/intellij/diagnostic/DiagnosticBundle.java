// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

/** Internal API. See a note in {@link MessagePool}. */
@ApiStatus.Internal
public final class DiagnosticBundle {
  public static final String BUNDLE = "messages.DiagnosticBundle";

  private static final DynamicBundle INSTANCE = new DynamicBundle(DiagnosticBundle.class, BUNDLE);

  private DiagnosticBundle() {}

  public static @NotNull @Nls String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    return INSTANCE.getMessage(key, params);
  }
}
