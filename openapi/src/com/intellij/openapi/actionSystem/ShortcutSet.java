/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.actionSystem;

public interface ShortcutSet {
  /**
   * @return array of keyboard <code>Shortcut</code>s that are in the set.
   * The method returns an empty array if there are no keyboard <code>Shortcuts</code>
   */
  Shortcut[] getShortcuts();
}
