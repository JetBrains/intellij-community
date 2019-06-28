// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import com.intellij.ide.plugins.PluginManagerConfigurableNew;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.AbstractLayoutManager;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class PluginsGridLayout extends AbstractLayoutManager {
  private final int myFirstVOffset = JBUIScale.scale(10);
  private final int myMiddleVOffset = JBUIScale.scale(20);
  private final int myLastVOffset = JBUIScale.scale(30);
  private final int myMiddleHOffset = JBUIScale.scale(1);

  private final Dimension myCellSize = new Dimension();
  private final ComponentCache myCache = new ComponentCache();

  @Override
  public Dimension preferredLayoutSize(@NotNull Container parent) {
    calculateCellSize(parent);

    int width = PluginManagerConfigurableNew.getParentWidth(parent);
    if (width == 0) {
      width = JBUIScale.scale(740);
    }
    int cellWidth = myCellSize.width;
    int columns = width / (cellWidth + myMiddleHOffset);

    if (columns < 2) {
      columns = 2;
    }
    width = columns * (cellWidth + myMiddleHOffset) - myMiddleHOffset;

    int height = 0;
    int cellHeight = myCellSize.height;
    List<UIPluginGroup> groups = ((PluginsGroupComponent)parent).getGroups();

    for (UIPluginGroup group : groups) {
      height += group.panel.getPreferredSize().height;

      int plugins = group.plugins.size();
      int rows;

      if (plugins <= columns) {
        rows = 1;
      }
      else {
        rows = plugins / columns;
        if (plugins > rows * columns) {
          rows++;
        }
      }

      height += myFirstVOffset + rows * (cellHeight + myMiddleVOffset) - myMiddleVOffset + myLastVOffset;
    }

    return new Dimension(width, height);
  }

  @Override
  public void layoutContainer(@NotNull Container parent) {
    calculateCellSize(parent);

    List<UIPluginGroup> groups = ((PluginsGroupComponent)parent).getGroups();
    int width = parent.getWidth();
    int y = 0;
    int columns = Math.max(1, width / (myCellSize.width + myMiddleHOffset));

    for (UIPluginGroup group : groups) {
      Component component = group.panel;
      int height = component.getPreferredSize().height;
      component.setBounds(0, y, width, height);
      y += height + myFirstVOffset;
      y += layoutPlugins(group.plugins, y, columns);
    }
  }

  private void calculateCellSize(@NotNull Container parent) {
    if (myCache.isCached(parent)) {
      return;
    }

    myCellSize.width = 0;
    myCellSize.height = 0;

    List<UIPluginGroup> groups = ((PluginsGroupComponent)parent).getGroups();

    for (UIPluginGroup group : groups) {
      for (CellPluginComponent plugin : group.plugins) {
        plugin.doLayout();
        Dimension size = plugin.getPreferredSize();
        myCellSize.width = Math.max(myCellSize.width, size.width);
        myCellSize.height = Math.max(myCellSize.height, size.height);
      }
    }
  }

  private int layoutPlugins(@NotNull List<CellPluginComponent> plugins, int startY, int columns) {
    int x = 0;
    int y = 0;
    int width = myCellSize.width;
    int height = myCellSize.height;
    int column = 0;

    for (int i = 0, size = plugins.size(), last = size - 1; i < size; i++) {
      plugins.get(i).setBounds(x, startY + y, width, height);
      x += width + myMiddleHOffset;

      if (++column == columns || i == last) {
        x = 0;
        y += height + myMiddleVOffset;
        column = 0;
      }
    }

    y += (myLastVOffset - myMiddleVOffset);

    return y;
  }
}