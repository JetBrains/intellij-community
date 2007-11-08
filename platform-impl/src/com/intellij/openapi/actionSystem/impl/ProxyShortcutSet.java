package com.intellij.openapi.actionSystem.impl;

import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.actionSystem.ShortcutSet;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;

/**
 * Please do not use this class outside impl package!!!
 * Please do not use this class even if you managed to make it public!!!
 * Thank you in advance. 
 *    The UI Engineers. 
 */ 
final class ProxyShortcutSet implements ShortcutSet {
  private final String myActionId;
  private KeymapManager myKeymapManager;

  public ProxyShortcutSet(String actionId, KeymapManager keymapManager) {
    myActionId = actionId;
    myKeymapManager = keymapManager;
  }

  public Shortcut[] getShortcuts() {
    Keymap keymap=myKeymapManager.getActiveKeymap();
    return keymap.getShortcuts(myActionId);
  }
}
