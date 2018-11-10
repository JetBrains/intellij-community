// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.keymap;

import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface KeymapManagerListener {
  Topic<KeymapManagerListener> TOPIC = new Topic<>("KeymapManagerListener", KeymapManagerListener.class);

  default void activeKeymapChanged(@Nullable Keymap keymap) {
  }

  default void shortcutChanged(@NotNull Keymap keymap, @NotNull String actionId) {
  }
}
