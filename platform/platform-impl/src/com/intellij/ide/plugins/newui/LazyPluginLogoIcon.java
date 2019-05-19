// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author Alexander Lobas
 */
class LazyPluginLogoIcon implements PluginLogoIconProvider {
  private PluginLogoIconProvider myLogoIcon;
  private final Map<String, LazyIcon> myIcons = new HashMap<>();

  LazyPluginLogoIcon(@NotNull PluginLogoIconProvider logoIcon) {
    myLogoIcon = logoIcon;
  }

  @NotNull
  @Override
  public Icon getIcon(boolean big, boolean jb, boolean error, boolean disabled) {
    String key = String.valueOf(big) + jb + error + disabled;
    LazyIcon icon = myIcons.get(key);
    if (icon == null) {
      myIcons.put(key, icon = new LazyIcon(new boolean[]{big, jb, error, disabled}));
      icon.setIcon(myLogoIcon);
    }
    return icon;
  }

  void setLogoIcon(@NotNull PluginLogoIconProvider logoIcon) {
    myLogoIcon = logoIcon;

    for (LazyIcon icon : myIcons.values()) {
      icon.setIcon(logoIcon);
    }
  }

  private static class LazyIcon implements Icon {
    private final boolean[] myState;
    private Icon myIcon;
    private final Set<Component> myComponents = new HashSet<>();

    private LazyIcon(@NotNull boolean[] state) {
      myState = state;
    }

    private void setIcon(@NotNull PluginLogoIconProvider logoIcon) {
      myIcon = logoIcon.getIcon(myState[0], myState[1], myState[2], myState[3]);
      for (Component component : myComponents) {
        if (component.isShowing()) {
          component.repaint();
        }
      }
    }

    @Override
    public void paintIcon(Component component, Graphics g, int x, int y) {
      myComponents.add(component);
      myIcon.paintIcon(component, g, x, y);
    }

    @Override
    public int getIconWidth() {
      return myIcon.getIconWidth();
    }

    @Override
    public int getIconHeight() {
      return myIcon.getIconHeight();
    }
  }
}