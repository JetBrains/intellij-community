// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.analysis;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

import java.util.function.Supplier;

public final class JvmAnalysisBundle extends DynamicBundle {
  public static @Nls String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    return ourInstance.getMessage(key, params);
  }

  public static @NotNull Supplier<@Nls String> messagePointer(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key,
                                                              Object @NotNull ... params) {
    return ourInstance.getLazyMessage(key, params);
  }

  public static final @NonNls String BUNDLE = "messages.JvmAnalysisBundle";
  private static final JvmAnalysisBundle ourInstance = new JvmAnalysisBundle();

  private JvmAnalysisBundle() {
    super(BUNDLE);
  }
}
