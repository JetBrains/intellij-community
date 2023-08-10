// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.core;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.*;

public final class CoreBundle {
  public static final @NonNls String BUNDLE = "messages.CoreBundle";
  private static final DynamicBundle INSTANCE = new DynamicBundle(CoreBundle.class, BUNDLE);

  private CoreBundle() {
  }

  public static @NotNull @Nls String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    return INSTANCE.getMessage(key, params);
  }

  public static @Nls String messageOrNull(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    return INSTANCE.messageOrNull(key, params);
  }

  @ApiStatus.Internal
  public static void clearCache() {
    INSTANCE.clearLocaleCache();
  }
}
