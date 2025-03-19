// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.*;

import java.util.function.Supplier;

@ApiStatus.Internal
public final class LanguageAndRegionBundle {

  public static final @NonNls String BUNDLE_FQN = "messages.LanguageAndRegionBundle";
  private static final DynamicBundle BUNDLE = new DynamicBundle(LanguageAndRegionBundle.class, BUNDLE_FQN);

  private LanguageAndRegionBundle() { }

  public static @Nls @NotNull String message(@PropertyKey(resourceBundle = BUNDLE_FQN) @NotNull String key,
                                             @Nullable Object @NotNull ... params) {
    return BUNDLE.getMessage(key, params);
  }

  public static @NotNull Supplier<@Nls @NotNull String> messagePointer(@PropertyKey(resourceBundle = BUNDLE_FQN) @NotNull String key,
                                                                       @Nullable Object @NotNull ... params) {
    return BUNDLE.getLazyMessage(key, params);
  }
}
