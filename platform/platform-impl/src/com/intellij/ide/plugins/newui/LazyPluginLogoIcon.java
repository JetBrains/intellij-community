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
final class LazyPluginLogoIcon implements PluginLogoIconProvider {
  private PluginLogoIconProvider logoIcon;
  private final Map<String, LazyIcon> icons = new HashMap<>();

  LazyPluginLogoIcon(@NotNull PluginLogoIconProvider logoIcon) {
    this.logoIcon = logoIcon;
  }

  @Override
  public synchronized @NotNull Icon getIcon(boolean big, boolean error, boolean disabled) {
    String key = String.valueOf(big) + error + disabled;
    LazyIcon icon = icons.get(key);
    if (icon == null) {
      icons.put(key, icon = new LazyIcon(new boolean[]{big, error, disabled}));
      icon.setIcon(logoIcon, false);
    }
    return icon;
  }

  synchronized void setLogoIcon(@NotNull PluginLogoIconProvider logoIcon) {
    this.logoIcon = logoIcon;
    for (LazyIcon icon : icons.values()) {
      icon.setIcon(logoIcon, true);
    }
  }

  private static final class LazyIcon implements Icon {
    private final boolean[] myState;
    private Icon icon;
    private Set<Component> components = ContainerUtil.createWeakSet();

    private LazyIcon(boolean @NotNull [] state) {
      myState = state;
    }

    private void setIcon(@NotNull PluginLogoIconProvider logoIcon, boolean repaint) {
      if (repaint) {
        ApplicationManager.getApplication().invokeLater(() -> {
          setIcon(logoIcon);
          Set<Component> components = this.components;
          this.components = null;

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
      icon = logoIcon.getIcon(myState[0], myState[1], myState[2]);
    }

    @Override
    public void paintIcon(Component component, Graphics g, int x, int y) {
      icon.paintIcon(component, g, x, y);

      if (components != null) {
        components.add(component);
      }
    }

    @Override
    public int getIconWidth() {
      return icon.getIconWidth();
    }

    @Override
    public int getIconHeight() {
      return icon.getIconHeight();
    }
  }
}