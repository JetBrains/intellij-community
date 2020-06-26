// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.idea;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

import java.util.function.Supplier;

public final class ActionsBundle extends DynamicBundle {
  @NonNls private static final String IDEA_ACTIONS_BUNDLE = "messages.ActionsBundle";

  private static final ActionsBundle ourInstance = new ActionsBundle();

  private ActionsBundle() {
    super(IDEA_ACTIONS_BUNDLE);
  }

  public static String message(@NotNull @PropertyKey(resourceBundle = IDEA_ACTIONS_BUNDLE) String key, Object @NotNull ... params) {
    return ourInstance.getMessage(key, params);
  }

  @NotNull
  public static Supplier<String> messagePointer(@NotNull @PropertyKey(resourceBundle = IDEA_ACTIONS_BUNDLE) String key, Object @NotNull ... params) {
    return ourInstance.getLazyMessage(key, params);
  }

  public static String actionText(@NonNls String actionId) {
    return message("action." + actionId + ".text");
  }

  public static String groupText(@NonNls String actionId) {
    return message("group." + actionId + ".text");
  }

  public static String actionDescription(@NonNls String actionId) {
    return message("action." + actionId + ".description");
  }
}
