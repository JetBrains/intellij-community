package com.intellij.openapi.keymap;

import com.intellij.openapi.components.ServiceManager;

import javax.swing.*;

/**
 * @author yole
 */
public abstract class KeymapGroupFactory {
  public static KeymapGroupFactory getInstance() {
    return ServiceManager.getService(KeymapGroupFactory.class);
  }

  public abstract KeymapGroup createGroup(String name);
  public abstract KeymapGroup createGroup(String name, Icon closedIcon, Icon openIcon);
}