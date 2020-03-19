// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

import java.util.function.Supplier;

public class UIBundle extends DynamicBundle {
  public static final String BUNDLE = "messages.UIBundle";
  private static final UIBundle INSTANCE = new UIBundle();

  private UIBundle() {
    super(BUNDLE);
  }

  @NotNull
  public static String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    if (!INSTANCE.containsKey(key)) {
      return UtilUiBundle.message(key, params);
    }
    return INSTANCE.getMessage(key, params);
  }

  @NotNull
  public static Supplier<String> messagePointer(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    if (!INSTANCE.containsKey(key)) {
      return () -> UtilUiBundle.message(key, params);
    }
    return INSTANCE.getLazyMessage(key, params);
  }
}