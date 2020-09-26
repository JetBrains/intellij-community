// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.idea;

import com.intellij.DynamicBundle;
import com.intellij.openapi.util.NlsActions;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

import java.util.function.Supplier;

public final class ActionsBundle {
  @NonNls private static final String BUNDLE = "messages.ActionsBundle";
  private static final DynamicBundle INSTANCE = new DynamicBundle(BUNDLE);

  private ActionsBundle() {
  }

  public static @Nls String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    return INSTANCE.getMessage(key, params);
  }

  @NotNull
  public static Supplier<@Nls String> messagePointer(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    return INSTANCE.getLazyMessage(key, params);
  }

  public static @NlsActions.ActionText String actionText(@NonNls String actionId) {
    return message("action." + actionId + ".text");
  }

  public static @NlsActions.ActionText String groupText(@NonNls String actionId) {
    return message("group." + actionId + ".text");
  }

  public static @NlsActions.ActionDescription String actionDescription(@NonNls String actionId) {
    return message("action." + actionId + ".description");
  }
}
