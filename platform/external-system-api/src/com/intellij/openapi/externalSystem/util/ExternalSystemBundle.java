// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.util;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

import java.util.function.Supplier;

/**
 * Internal: plugins may not reuse platform i18n messages.
 */
@ApiStatus.Internal
public final class ExternalSystemBundle {
  public static final @NonNls String PATH_TO_BUNDLE = "messages.ExternalSystemBundle";
  private static final DynamicBundle BUNDLE = new DynamicBundle(ExternalSystemBundle.class, PATH_TO_BUNDLE);

  public static @Nls String message(@NotNull @PropertyKey(resourceBundle = PATH_TO_BUNDLE) String key, Object @NotNull ... params) {
    return BUNDLE.getMessage(key, params);
  }

  public static Supplier<@Nls String> messagePointer(@NotNull @PropertyKey(resourceBundle = PATH_TO_BUNDLE) String key,
                                                     Object @NotNull ... params) {
    return BUNDLE.getLazyMessage(key, params);
  }
}
