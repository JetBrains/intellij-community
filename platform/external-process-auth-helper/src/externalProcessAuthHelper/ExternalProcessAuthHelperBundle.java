// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.externalProcessAuthHelper;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

import java.util.function.Supplier;

public final class ExternalProcessAuthHelperBundle {
  public static final @NonNls String BUNDLE = "messages.ExternalProcessAuthHelperBundle";
  private static final DynamicBundle INSTANCE = new DynamicBundle(ExternalProcessAuthHelperBundle.class, BUNDLE);

  private ExternalProcessAuthHelperBundle() {}

  public static @NotNull @Nls String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    return INSTANCE.getMessage(key, params);
  }

  public static @NotNull Supplier<@Nls String> messagePointer(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    return INSTANCE.getLazyMessage(key, params);
  }

  /**
   * @deprecated prefer {@link #message(String, Object...)} instead
   */
  @Deprecated(forRemoval = true)
  public static @NotNull @Nls String getString(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key) {
    return message(key);
  }
}