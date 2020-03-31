/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.actionSystem;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * Default implementation of the {@link ShortcutSet} interface.
 */

public final class CustomShortcutSet implements ShortcutSet {

  public static final CustomShortcutSet EMPTY = new CustomShortcutSet(Shortcut.EMPTY_ARRAY);

  private final Shortcut[] myShortcuts;

  /**
   * Creates {@code CustomShortcutSet} which contains only one
   * single stroke keyboard shortcut.
   */
  public CustomShortcutSet(@NotNull KeyStroke keyStroke){
    this(new KeyboardShortcut(keyStroke, null));
  }

  /**
   * Creates {@code CustomShortcutSet} which contains specified keyboard and
   * mouse shortcuts.
   *
   * @param shortcuts keyboard shortcuts
   */
  public CustomShortcutSet(Shortcut @NotNull ... shortcuts){
    myShortcuts = shortcuts.length == 0 ? Shortcut.EMPTY_ARRAY : shortcuts.clone();
  }

  public CustomShortcutSet(Integer @NotNull ... keyCodes) {
    myShortcuts = ContainerUtil.map(keyCodes, integer -> new KeyboardShortcut(KeyStroke.getKeyStroke(integer, 0), null), Shortcut.EMPTY_ARRAY);
  }

  @Override
  public Shortcut @NotNull [] getShortcuts(){
    return myShortcuts.length == 0 ? Shortcut.EMPTY_ARRAY : myShortcuts.clone();
  }

  @NotNull
  public static CustomShortcutSet fromString(@NonNls String @NotNull ... keyboardShortcuts) {
    final KeyboardShortcut[] shortcuts = new KeyboardShortcut[keyboardShortcuts.length];
    for (int i = 0; i < keyboardShortcuts.length; i++) {
      shortcuts[i] = KeyboardShortcut.fromString(keyboardShortcuts[i]);
    }
    return new CustomShortcutSet(shortcuts);
  }
}
