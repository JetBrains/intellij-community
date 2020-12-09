// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.core;

import com.intellij.BundleBase;
import com.intellij.DynamicBundle;
import org.jetbrains.annotations.*;

public final class CoreBundle {
  public static final @NonNls String BUNDLE = "messages.CoreBundle";
  private static final DynamicBundle INSTANCE = new DynamicBundle(BUNDLE);

  private CoreBundle() {
  }

  public static @NotNull @Nls String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    return BundleBase.messageOrDefault(INSTANCE.getResourceBundle(CoreBundle.class.getClassLoader()), key, null, params);
  }

  @ApiStatus.Internal
  public static void clearCache() {
    INSTANCE.clearLocaleCache();
  }
}
