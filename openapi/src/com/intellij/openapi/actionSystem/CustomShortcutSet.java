/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.actionSystem;

import javax.swing.*;

public final class CustomShortcutSet implements ShortcutSet {
  private final Shortcut[] myShortcuts;

  /**
   * Creates <code>CustomShortcutSet</code> which contains only one
   * single stroke keyboard shortcut.
   */
  public CustomShortcutSet(KeyStroke keyStroke){
    this(new Shortcut[]{new KeyboardShortcut(keyStroke, null)});
  }

  /**
   * Creates <code>CustomShortcutSet</code> which contains specified keyboard and
   * mouse shortcuts.
   *
   * @param shortcuts keyboard shortcuts
   */
  public CustomShortcutSet(Shortcut[] shortcuts){
    myShortcuts = (Shortcut[])shortcuts.clone();
  }

  public Shortcut[] getShortcuts(){
    return (Shortcut[])myShortcuts.clone();
  }
}