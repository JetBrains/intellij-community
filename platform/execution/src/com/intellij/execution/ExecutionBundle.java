// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution;

import com.intellij.AbstractBundle;
import com.intellij.DynamicBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

import java.util.function.Supplier;

public final class ExecutionBundle extends DynamicBundle {
  public static @NotNull @Nls String message(@NotNull @PropertyKey(resourceBundle = PATH_TO_BUNDLE) String key,
                                             Object @NotNull ... params) {
    return ourInstance.getMessage(key, params);
  }

  public static @NotNull Supplier<@Nls String> messagePointer(@NotNull @PropertyKey(resourceBundle = PATH_TO_BUNDLE) String key,
                                                              Object @NotNull ... params) {
    return ourInstance.getLazyMessage(key, params);
  }

  public static final String PATH_TO_BUNDLE = "messages.ExecutionBundle";
  private static final AbstractBundle ourInstance = new ExecutionBundle();

  private ExecutionBundle() {
    super(PATH_TO_BUNDLE);
  }
}
