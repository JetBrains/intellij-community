// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui;

import com.intellij.util.ui.AbstractLayoutManager;
import com.intellij.util.ui.AnimatedIcon;
import com.intellij.util.ui.JBValue;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * @author Alexander Lobas
 */
@ApiStatus.Internal
public final class PluginListLayout extends AbstractLayoutManager implements PagePluginLayout {
  private final JBValue myGroupGap = new JBValue.Float(10);
  private int myMiddleLineHeight;

  @Override
  public Dimension preferredLayoutSize(Container parent) {
    int height = 0;
    int count = parent.getComponentCount();

    int lines = 0;
    myMiddleLineHeight = 0;

    for (int i = 0; i < count; i++) {
      Component component = parent.getComponent(i);
      if (component instanceof AnimatedIcon) {
        continue;
      }
      int lineHeight = component.getPreferredSize().height;
      height += lineHeight;
      if (component instanceof ListPluginComponent) {
        myMiddleLineHeight += lineHeight;
        lines++;
      }
    }

    calculateLineHeight(lines);

    int size = ((PluginsGroupComponent)parent).getGroups().size();
    if (size > 1) {
      height += myGroupGap.get() * (size - 1);
    }

    return new Dimension(0, height);
  }

  @Override
  public void layoutContainer(Container parent) {
    List<UIPluginGroup> groups = ((PluginsGroupComponent)parent).getGroups();
    int width = parent.getWidth();
    int y = 0;
    int groupGap = myGroupGap.get();

    int lines = 0;
    myMiddleLineHeight = 0;

    for (UIPluginGroup group : groups) {
      Component component = group.panel;
      int height = component.getPreferredSize().height;
      component.setBounds(0, y, width, height);
      y += height;

      for (ListPluginComponent plugin : group.plugins) {
        int lineHeight = plugin.getPreferredSize().height;
        plugin.setBounds(0, y, width, lineHeight);
        y += lineHeight;
        myMiddleLineHeight += lineHeight;
      }

      lines += group.plugins.size();
      y += groupGap;
    }

    calculateLineHeight(lines);
  }

  public void calculateLineHeight(int lines) {
    if (lines == 0 || myMiddleLineHeight == 0) {
      myMiddleLineHeight = 10;
    }
    else {
      myMiddleLineHeight /= lines;
    }
  }

  @Override
  public int getPageCount(@NotNull JComponent parent) {
    return parent.getVisibleRect().height / myMiddleLineHeight;
  }
}