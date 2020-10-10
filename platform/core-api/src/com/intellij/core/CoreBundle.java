// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.core;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.*;

import java.util.function.Supplier;

public final class CoreBundle extends DynamicBundle {
  private static final @NonNls String BUNDLE = "messages.CoreBundle";
  public static final CoreBundle INSTANCE = new CoreBundle();

  private CoreBundle() {
    super(BUNDLE);
  }

  public static @NotNull @Nls String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    return message(INSTANCE.getResourceBundle(CoreBundle.class.getClassLoader()), key, params);
  }

  public static @NotNull Supplier<@Nls String> messagePointer(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    return INSTANCE.getLazyMessage(key, params);
  }

  @ApiStatus.Internal
  public static void clearCache() {
    INSTANCE.clearLocaleCache();
  }
}
