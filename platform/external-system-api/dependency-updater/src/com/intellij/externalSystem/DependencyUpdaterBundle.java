// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.externalSystem;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

import java.util.function.Supplier;

@ApiStatus.Internal
public final class DependencyUpdaterBundle {

  public static @Nls String message(@NotNull @PropertyKey(resourceBundle = PATH_TO_BUNDLE) String key, Object @NotNull ... params) {
    return bundle.getMessage(key, params);
  }

  public static @NotNull Supplier<@Nls String> messagePointer(@NotNull @PropertyKey(resourceBundle = PATH_TO_BUNDLE) String key,
                                                              Object @NotNull ... params) {
    return bundle.getLazyMessage(key, params);
  }

  private static final String PATH_TO_BUNDLE = "messages.DependencyUpdaterBundle";
  private static final DynamicBundle bundle = new DynamicBundle(DependencyUpdaterBundle.class, PATH_TO_BUNDLE);
}