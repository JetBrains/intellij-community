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

import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * Default implementation of the {@link ShortcutSet} interface.
 */

public final class CustomShortcutSet implements ShortcutSet {
  private final Shortcut[] myShortcuts;

  /**
   * Creates <code>CustomShortcutSet</code> which contains only one
   * single stroke keyboard shortcut.
   */
  public CustomShortcutSet(@NotNull KeyStroke keyStroke){
    this(new KeyboardShortcut(keyStroke, null));
  }

  public CustomShortcutSet() {
    myShortcuts = Shortcut.EMPTY_ARRAY;
  }

  /**
   * Creates <code>CustomShortcutSet</code> which contains specified keyboard and
   * mouse shortcuts.
   *
   * @param shortcuts keyboard shortcuts
   */
  public CustomShortcutSet(@NotNull Shortcut... shortcuts){
    myShortcuts = shortcuts.length == 0 ? Shortcut.EMPTY_ARRAY : shortcuts.clone();
  }

  public CustomShortcutSet(Integer... keyCodes) {
    myShortcuts = ContainerUtil.map(keyCodes, new Function<Integer, Shortcut>() {
      @Override
      public Shortcut fun(Integer integer) {
        return new KeyboardShortcut(KeyStroke.getKeyStroke(integer, 0), null);
      }
    }, Shortcut.EMPTY_ARRAY);
  }

  @Override
  @NotNull
  public Shortcut[] getShortcuts(){
    return myShortcuts.length == 0 ? Shortcut.EMPTY_ARRAY : myShortcuts.clone();
  }

  @NotNull
  public static CustomShortcutSet fromString(@NotNull String... keyboardShortcuts) {
    final KeyboardShortcut[] shortcuts = new KeyboardShortcut[keyboardShortcuts.length];
    for (int i = 0; i < keyboardShortcuts.length; i++) {
      shortcuts[i] = KeyboardShortcut.fromString(keyboardShortcuts[i]);
    }
    return new CustomShortcutSet(shortcuts);
  }
}
