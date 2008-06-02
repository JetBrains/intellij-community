package com.intellij.openapi.keymap.ex;

import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.options.SchemesManager;

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

  public abstract void setActiveKeymap(Keymap activeKeymap);

  public abstract void bindShortcuts(String sourceActionId, String targetActionId);
  public abstract Set<String> getBoundActions();
  public abstract String getActionBinding(String actionId);

  public abstract SchemesManager<Keymap> getSchemesManager();
}
