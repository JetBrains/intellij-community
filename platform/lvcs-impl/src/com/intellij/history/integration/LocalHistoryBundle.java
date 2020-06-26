// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.history.integration;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

public final class LocalHistoryBundle extends DynamicBundle {
  public static String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    return INSTANCE.getMessage(key, params);
  }

  private static final String BUNDLE = "messages.LocalHistoryBundle";
  private static final LocalHistoryBundle INSTANCE = new LocalHistoryBundle();

  private LocalHistoryBundle() {
    super(BUNDLE);
  }
}