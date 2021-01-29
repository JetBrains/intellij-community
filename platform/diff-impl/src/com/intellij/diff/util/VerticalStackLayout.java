// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.util;

import com.intellij.util.ui.AbstractLayoutManager;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

class VerticalStackLayout extends AbstractLayoutManager {
  private final int myVGap;

  VerticalStackLayout(int vGap) {
    myVGap = vGap;
  }

  @Override
  public Dimension preferredLayoutSize(Container parent) {
    int totalWidth = 0;
    int totalHeight = 0;

    for (Component component : parent.getComponents()) {
      Dimension size = getPreferredSize(component);

      totalWidth = Math.max(size.width, totalWidth);
      if (size.height != 0 && totalHeight != 0) totalHeight += myVGap;
      totalHeight += size.height;
    }

    return new Dimension(totalWidth, totalHeight);
  }

  @Override
  public void layoutContainer(@NotNull Container parent) {
    int width = parent.getWidth();

    int y = 0;
    for (Component component : parent.getComponents()) {
      Dimension size = getPreferredSize(component);

      component.setBounds(0, y, width, size.height);
      if (size.height != 0) y += myVGap;
      y += size.height;
    }
  }

  @NotNull
  private static Dimension getPreferredSize(@NotNull Component component) {
    return component.isVisible() ? component.getPreferredSize() : new Dimension();
  }
}
