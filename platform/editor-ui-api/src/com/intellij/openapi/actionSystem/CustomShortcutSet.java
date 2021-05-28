// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collection;

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

  public static @NotNull CustomShortcutSet fromStrings(@NotNull Collection<@NonNls String> shortcuts) {
    return new CustomShortcutSet(shortcuts.stream().map(KeyboardShortcut::fromString).toArray(KeyboardShortcut[]::new));
  }
}
