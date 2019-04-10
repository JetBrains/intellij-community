// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import com.intellij.util.ui.AbstractLayoutManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class PluginListLayout extends AbstractLayoutManager implements PagePluginLayout {
  @Override
  public Dimension preferredLayoutSize(Container parent) {
    int height = 0;
    int count = parent.getComponentCount();

    for (int i = 0; i < count; i++) {
      height += parent.getComponent(i).getPreferredSize().height;
    }

    return new Dimension(0, height);
  }

  @Override
  public void layoutContainer(Container parent) {
    List<UIPluginGroup> groups = ((PluginsGroupComponent)parent).getGroups();
    int width = parent.getWidth();
    int y = 0;

    for (UIPluginGroup group : groups) {
      Component component = group.panel;
      int height = component.getPreferredSize().height;
      component.setBounds(0, y, width, height);
      y += height;

      for (CellPluginComponent plugin : group.plugins) {
        int lineHeight = plugin.getPreferredSize().height;
        plugin.setBounds(0, y, width, lineHeight);
        y += lineHeight;
      }
    }
  }

  @Override
  public int getPageCount(@NotNull JComponent parent) {
    return 0;  // TODO: Auto-generated method stub
  }
}