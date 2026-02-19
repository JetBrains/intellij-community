// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.util;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

import java.util.function.Supplier;

public class ExternalSystemBundle extends DynamicBundle {
  public static final @NonNls String PATH_TO_BUNDLE = "messages.ExternalSystemBundle";
  private static final ExternalSystemBundle BUNDLE = new ExternalSystemBundle();

  public static @Nls String message(@NotNull @PropertyKey(resourceBundle = PATH_TO_BUNDLE) String key, Object @NotNull ... params) {
    return BUNDLE.getMessage(key, params);
  }

  public static Supplier<@Nls String> messagePointer(@NotNull @PropertyKey(resourceBundle = PATH_TO_BUNDLE) String key, Object @NotNull ... params) {
    return BUNDLE.getLazyMessage(key, params);
  }

  public ExternalSystemBundle() {
    super(PATH_TO_BUNDLE);
  }
}
