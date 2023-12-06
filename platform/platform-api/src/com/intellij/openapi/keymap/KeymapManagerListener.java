// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.keymap;

import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public interface KeymapManagerListener {
  @Topic.AppLevel
  Topic<KeymapManagerListener> TOPIC = new Topic<>(KeymapManagerListener.class, Topic.BroadcastDirection.NONE);

  default void keymapAdded(@NotNull Keymap keymap) {
  }

  default void keymapRemoved(@NotNull Keymap keymap) {
  }

  default void activeKeymapChanged(@Nullable Keymap keymap) {
  }

  /**
   * @deprecated Use {@link #shortcutsChanged(Keymap, Collection, boolean)}
   */
  @SuppressWarnings("unused")
  @Deprecated
  default void shortcutChanged(@NotNull Keymap keymap, @NonNls @NotNull String actionId) {
  }

  /**
   * @deprecated Use {@link #shortcutsChanged(Keymap, Collection, boolean)}
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated
  default void shortcutChanged(@NotNull Keymap keymap, @NonNls @NotNull String actionId, @SuppressWarnings("unused") boolean fromSettings) {
    shortcutChanged(keymap, actionId);
  }

  default void shortcutsChanged(@NotNull Keymap keymap, @NonNls @NotNull Collection<String> actionIds, boolean fromSettings) {
    for (String actionId : actionIds) {
      shortcutChanged(keymap, actionId, fromSettings);
    }
  }
}
