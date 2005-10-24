package com.intellij.openapi.keymap.ex;

import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;

import java.util.Set;

public abstract class KeymapManagerEx extends KeymapManager {
  public static KeymapManagerEx getInstanceEx(){
    return (KeymapManagerEx)getInstance();
  }

  /**
   * @return all available keymaps. The method return an aempty array if no
   * keymaps are available.
   */
  public abstract Keymap[] getAllKeymaps();

  public abstract void addKeymapManagerListener(KeymapManagerListener listener);

  public abstract void removeKeymapManagerListener(KeymapManagerListener listener);

  public abstract void setActiveKeymap(Keymap activeKeymap);

  public abstract void bindShortcuts(String sourceActionId, String targetActionId);
  public abstract Set<String> getBoundActions();
  public abstract String getActionBinding(String actionId);
}
