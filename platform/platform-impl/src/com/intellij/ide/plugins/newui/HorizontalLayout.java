// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import com.intellij.util.ui.AbstractLayoutManager;
import com.intellij.util.ui.JBInsets;

import java.awt.*;

/**
 * @author Alexander Lobas
 */
public class HorizontalLayout extends AbstractLayoutManager {
  protected final int myOffset;

  public HorizontalLayout(int offset) {
    myOffset = offset;
  }

  @Override
  public Dimension preferredLayoutSize(Container parent) {
    int width = 0;
    int height = 0;
    int count = parent.getComponentCount();

    for (int i = 0; i < count; i++) {
      Dimension size = parent.getComponent(i).getPreferredSize();
      width += size.width;
      height = Math.max(height, size.height);
    }

    width += (count - 1) * myOffset;

    Dimension size = new Dimension(width, height);
    JBInsets.addTo(size, parent.getInsets());
    return size;
  }

  @Override
  public void layoutContainer(Container parent) {
    Insets insets = parent.getInsets();
    int height = parent.getHeight() - insets.top - insets.bottom;
    int x = insets.left;
    int count = parent.getComponentCount();

    for (int i = 0; i < count; i++) {
      Component component = parent.getComponent(i);
      Dimension size = component.getPreferredSize();
      component.setBounds(x, insets.top + (height - size.height) / 2, size.width, size.height);
      x += size.width + myOffset;
    }
  }
}