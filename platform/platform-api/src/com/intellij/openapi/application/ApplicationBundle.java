// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.*;

import java.util.function.Supplier;

/**
 * Provides access to localized properties of the IntelliJ Platform.
 */
public final class ApplicationBundle extends DynamicBundle {
  public static final String BUNDLE = "messages.ApplicationBundle";
  public static final ApplicationBundle INSTANCE = new ApplicationBundle();

  private ApplicationBundle() {
    super(BUNDLE);
  }

  public static @NotNull @Nls String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    return INSTANCE.getMessage(key, params);
  }

  public static @NotNull Supplier<@Nls String> messagePointer(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    return INSTANCE.getLazyMessage(key, params);
  }
}
