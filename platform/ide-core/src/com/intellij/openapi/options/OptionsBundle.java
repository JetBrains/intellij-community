// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

import java.util.ResourceBundle;
import java.util.function.Supplier;

/**
 * Internal: plugins may not reuse platform i18n messages.
 */
@ApiStatus.Internal
public final class OptionsBundle {
  public static final String BUNDLE = "messages.OptionsBundle";

  private static final DynamicBundle INSTANCE = new DynamicBundle(OptionsBundle.class, BUNDLE);

  public static @Nls @NotNull String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    return INSTANCE.getMessage(key, params);
  }

  public static @NotNull Supplier<@Nls String> messagePointer(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key,
                                                              Object @NotNull ... params) {
    return INSTANCE.getLazyMessage(key, params);
  }

  public static boolean containsKey(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key) {
    return INSTANCE.containsKey(key);
  }

  public static ResourceBundle getResourceBundle() {
    return INSTANCE.getResourceBundle();
  }
}
