// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
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
      icon.setIcon(myLogoIcon, false);
    }
    return icon;
  }

  void setLogoIcon(@NotNull PluginLogoIconProvider logoIcon) {
    myLogoIcon = logoIcon;

    for (LazyIcon icon : myIcons.values()) {
      icon.setIcon(logoIcon, true);
    }
  }

  private static final class LazyIcon implements Icon {
    private final boolean[] myState;
    private Icon myIcon;
    private Set<Component> myComponents = ContainerUtil.createWeakSet();

    private LazyIcon(boolean @NotNull [] state) {
      myState = state;
    }

    private void setIcon(@NotNull PluginLogoIconProvider logoIcon, boolean repaint) {
      if (repaint) {
        ApplicationManager.getApplication().invokeLater(() -> {
          setIcon(logoIcon);
          Set<Component> components = myComponents;
          myComponents = null;

          for (Component component : components) {
            if (component.isShowing()) {
              component.repaint();
            }
          }
        }, ModalityState.any());
      }
      else {
        setIcon(logoIcon);
      }
    }

    private void setIcon(@NotNull PluginLogoIconProvider logoIcon) {
      myIcon = logoIcon.getIcon(myState[0], myState[1], myState[2], myState[3]);
    }

    @Override
    public void paintIcon(Component component, Graphics g, int x, int y) {
      myIcon.paintIcon(component, g, x, y);

      if (myComponents != null) {
        myComponents.add(component);
      }
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