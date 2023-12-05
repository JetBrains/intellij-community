// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Arrays;
import java.util.Collection;

/**
 * Default implementation of the {@link ShortcutSet} interface.
 */
public final class CustomShortcutSet implements ShortcutSet {
  public static final CustomShortcutSet EMPTY = new CustomShortcutSet(Shortcut.EMPTY_ARRAY);

  private final Shortcut[] shortcuts;

  /**
   * Creates {@code CustomShortcutSet} which contains only one
   * single stroke keyboard shortcut.
   */
  public CustomShortcutSet(@NotNull KeyStroke keyStroke) {
    this(new KeyboardShortcut(keyStroke, null));
  }

  /**
   * Creates {@code CustomShortcutSet} which contains specified keyboard and
   * mouse shortcuts.
   *
   * @param shortcuts keyboard shortcuts
   */
  public CustomShortcutSet(Shortcut @NotNull ... shortcuts) {
    this.shortcuts = shortcuts.length == 0 ? Shortcut.EMPTY_ARRAY : shortcuts.clone();
  }

  public CustomShortcutSet(Integer @NotNull ... keyCodes) {
    if (keyCodes.length == 0) {
      shortcuts = Shortcut.EMPTY_ARRAY;
    }
    else {
      shortcuts = Arrays.copyOf(Shortcut.EMPTY_ARRAY, keyCodes.length);
      for (int i = 0; i < keyCodes.length; i++) {
        shortcuts[i] = new KeyboardShortcut(KeyStroke.getKeyStroke(keyCodes[i], 0), null);
      }
    }
  }

  @Override
  public Shortcut @NotNull [] getShortcuts() {
    return shortcuts.length == 0 ? Shortcut.EMPTY_ARRAY : shortcuts.clone();
  }

  @Override
  public boolean hasShortcuts() {
    return shortcuts.length != 0;
  }

  public static @NotNull CustomShortcutSet fromString(@NonNls String @NotNull ... keyboardShortcuts) {
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
