// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import com.intellij.util.ui.AbstractLayoutManager;
import com.intellij.util.ui.JBInsets;
import org.jetbrains.annotations.NonNls;

import java.awt.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author Alexander Lobas
 * @deprecated use {@link com.intellij.ui.components.panels.VerticalLayout}
 */
@Deprecated
public class VerticalLayout extends AbstractLayoutManager {
  @NonNls public static final String FILL_HORIZONTAL = "fill_h";

  private final int myOffset;
  private final int myWidth;
  private final Set<Component> myFillComponents = new HashSet<>();
  private final Map<Component, Integer> myMaxComponents = new HashMap<>();

  public VerticalLayout(int offset) {
    this(offset, 0);
  }

  public VerticalLayout(int offset, int width) {
    myOffset = offset;
    myWidth = width;
  }

  @Override
  public void addLayoutComponent(Component component, Object constraints) {
    if (FILL_HORIZONTAL.equals(constraints)) {
      myFillComponents.add(component);
    }
    else if (constraints instanceof Integer) {
      myMaxComponents.put(component, (Integer)constraints);
    }
  }

  @Override
  public void removeLayoutComponent(Component component) {
    myFillComponents.remove(component);
  }

  @Override
  public Dimension preferredLayoutSize(Container parent) {
    int width = 0;
    int height = 0;
    int count = parent.getComponentCount();
    int visibleCount = 0;

    for (int i = 0; i < count; i++) {
      Component component = parent.getComponent(i);
      if (!component.isVisible()) {
        continue;
      }
      Dimension size = component.getPreferredSize();
      width = Math.max(width, size.width);
      height += size.height;
      visibleCount++;
    }

    if (visibleCount > 1) {
      height += (visibleCount - 1) * myOffset;
    }

    Dimension size = new Dimension(myWidth > 0 ? myWidth : width, height);
    JBInsets.addTo(size, parent.getInsets());
    return size;
  }

  @Override
  public void layoutContainer(Container parent) {
    Insets insets = parent.getInsets();
    int width = parent.getWidth() - insets.left - insets.right;
    int y = insets.top;
    int count = parent.getComponentCount();

    for (int i = 0; i < count; i++) {
      Component component = parent.getComponent(i);
      if (!component.isVisible()) {
        continue;
      }
      Dimension size = component.getPreferredSize();
      int componentWidth;
      if (myFillComponents.contains(component)) {
        componentWidth = width;
      }
      else {
        componentWidth = Math.min(width, size.width);
        Integer maxWidth = myMaxComponents.get(component);
        if (maxWidth != null) {
          componentWidth = Math.min(componentWidth, maxWidth);
        }
      }
      component.setBounds(insets.left, y, componentWidth, size.height);
      y += size.height + myOffset;
    }
  }
}