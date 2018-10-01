// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import com.intellij.ide.plugins.PluginManagerConfigurableNew;
import com.intellij.util.ui.AbstractLayoutManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class PluginsListLayout extends AbstractLayoutManager {
  private final ComponentCache myCache = new ComponentCache();
  int myLineHeight;

  @Override
  public Dimension preferredLayoutSize(@NotNull Container parent) {
    calculateLineHeight(parent);

    List<UIPluginGroup> groups = ((PluginsGroupComponent)parent).getGroups();
    int height = 0;

    for (UIPluginGroup group : groups) {
      height += group.panel.getPreferredSize().height;
      height += group.plugins.size() * myLineHeight;
    }

    return new Dimension(0, height);
  }

  @Override
  public void layoutContainer(@NotNull Container parent) {
    calculateLineHeight(parent);

    List<UIPluginGroup> groups = ((PluginsGroupComponent)parent).getGroups();
    int width = parent.getWidth();
    int y = 0;

    for (UIPluginGroup group : groups) {
      Component component = group.panel;
      int height = component.getPreferredSize().height;
      component.setBounds(0, y, width, height);
      y += height;

      for (CellPluginComponent plugin : group.plugins) {
        plugin.setBounds(0, y, width, myLineHeight);
        y += myLineHeight;
      }
    }
  }

  private void calculateLineHeight(@NotNull Container parent) {
    if (myCache.isCached(parent)) {
      return;
    }

    List<UIPluginGroup> groups = ((PluginsGroupComponent)parent).getGroups();
    int width = PluginManagerConfigurableNew.getParentWidth(parent) - parent.getInsets().right;

    myLineHeight = 0;

    for (UIPluginGroup group : groups) {
      for (CellPluginComponent plugin : group.plugins) {
        JEditorPane description = plugin.myDescription;
        if (description != null) {
          plugin.doLayout();
          int parentWidth = width - SwingUtilities.convertPoint(description.getParent(), description.getLocation(), plugin).x;
          if (parentWidth > 0) {
            description.putClientProperty("parent.width", new Integer(parentWidth));
          }
        }

        plugin.doLayout();
        myLineHeight = Math.max(myLineHeight, plugin.getPreferredSize().height);
      }
    }
  }
}