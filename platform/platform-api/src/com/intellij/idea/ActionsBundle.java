// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.idea;

import com.intellij.DynamicBundle;
import com.intellij.openapi.util.NlsActions;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

import java.util.function.Supplier;

public final class ActionsBundle {
  @NonNls public static final String IDEA_ACTIONS_BUNDLE = "messages.ActionsBundle";

  private static final DynamicBundle ourInstance = new DynamicBundle(ActionsBundle.class, IDEA_ACTIONS_BUNDLE);

  private ActionsBundle() {
  }

  public static @Nls String message(@NotNull @PropertyKey(resourceBundle = IDEA_ACTIONS_BUNDLE) String key, Object @NotNull ... params) {
    return ourInstance.containsKey(key) ? ourInstance.getMessage(key, params) : ActionsDeprecatedMessagesBundle.message(key, params);
  }

  @NotNull
  public static Supplier<@Nls String> messagePointer(@NotNull @PropertyKey(resourceBundle = IDEA_ACTIONS_BUNDLE) String key, Object @NotNull ... params) {
    return ourInstance.containsKey(key) ? ourInstance.getLazyMessage(key, params) : ActionsDeprecatedMessagesBundle.messagePointer(key, params);
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
