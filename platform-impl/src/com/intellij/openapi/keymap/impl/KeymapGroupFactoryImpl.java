package com.intellij.openapi.keymap.impl;

import com.intellij.openapi.keymap.KeymapGroup;
import com.intellij.openapi.keymap.KeymapGroupFactory;
import com.intellij.openapi.keymap.impl.ui.Group;

/**
 * @author yole
 */
public class KeymapGroupFactoryImpl extends KeymapGroupFactory {
  public KeymapGroup createGroup(final String name) {
    return new Group(name, null, null);
  }
}