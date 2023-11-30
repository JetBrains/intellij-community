// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

import java.util.function.Supplier;

public final class JavaBundle {
  public static final @NonNls String BUNDLE = "messages.JavaBundle";
  private static final DynamicBundle INSTANCE = new DynamicBundle(JavaBundle.class, BUNDLE);

  private JavaBundle() {
  }

  public static @NotNull @Nls String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    return INSTANCE.getMessage(key, params);
  }

  public static @Nls String partialMessage(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key,
                                           int unassignedParams,
                                           Object @NotNull ... params) {
    return INSTANCE.getPartialMessage(key, unassignedParams, params);
  }
  
  public static @NotNull Supplier<@Nls String> messagePointer(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    return INSTANCE.getLazyMessage(key, params);
  }
}
