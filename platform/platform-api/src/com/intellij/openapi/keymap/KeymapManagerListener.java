// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.keymap;

import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface KeymapManagerListener {
  @Topic.AppLevel
  Topic<KeymapManagerListener> TOPIC = new Topic<>(KeymapManagerListener.class, Topic.BroadcastDirection.NONE);

  default void keymapAdded(@NotNull Keymap keymap) {
  }

  default void keymapRemoved(@NotNull Keymap keymap) {
  }

  default void activeKeymapChanged(@Nullable Keymap keymap) {
  }

  default void shortcutChanged(@NotNull Keymap keymap, @NonNls @NotNull String actionId) {
  }

  default void shortcutChanged(@NotNull Keymap keymap, @NonNls @NotNull String actionId, boolean fromSettings) {
    shortcutChanged(keymap, actionId);
  }
}
