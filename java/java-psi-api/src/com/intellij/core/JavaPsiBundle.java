// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.core;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

import java.util.function.Supplier;

public final class JavaPsiBundle extends DynamicBundle {
  @NonNls public static final String BUNDLE = "messages.JavaPsiBundle";
  public static final JavaPsiBundle INSTANCE = new JavaPsiBundle();

  private JavaPsiBundle() { super(BUNDLE); }

  @NotNull
  public static String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    return INSTANCE.getMessage(key, params);
  }

  @NotNull
  public static Supplier<String> messagePointer(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    return INSTANCE.getLazyMessage(key, params);
  }

  public static boolean contains(String key) {
    return INSTANCE.containsKey(key);
  }

  @NotNull
  public static String visibilityPresentation(@NotNull String modifier) {
    return message("visibility.presentation." + modifier);
  }
}