// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem;

import org.jetbrains.annotations.NotNull;

/**
 * Represents a set of keyboard and/or mouse shortcuts.
 *
 * @see AnAction#getShortcutSet()
 * @see AnAction#setShortcutSet(ShortcutSet)
 */
public interface ShortcutSet {
  /**
   * @return array of keyboard {@code Shortcut}s that are in the set.
   * The method returns an empty array if there are no keyboard {@code Shortcuts}
   */
  Shortcut @NotNull [] getShortcuts();

  default boolean hasShortcuts() {
    return getShortcuts().length != 0;
  }
}
