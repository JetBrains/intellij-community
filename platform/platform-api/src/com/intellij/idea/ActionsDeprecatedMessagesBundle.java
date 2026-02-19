// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.idea;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

final class ActionsDeprecatedMessagesBundle {
  private static final @NonNls String BUNDLE = "messages.ActionsDeprecatedMessagesBundle";
  private static final DynamicBundle INSTANCE = new DynamicBundle(ActionsDeprecatedMessagesBundle.class, BUNDLE);

  private ActionsDeprecatedMessagesBundle() {
  }

  public static @NotNull @Nls String message(@NotNull String key, Object @NotNull ... params) {
    return INSTANCE.getMessage(key, params);
  }

  public static @NotNull Supplier<@Nls String> messagePointer(@NotNull String key, Object @NotNull ... params) {
    return INSTANCE.getLazyMessage(key, params);
  }
}
