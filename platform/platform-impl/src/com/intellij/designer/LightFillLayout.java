/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.designer;

import com.intellij.util.Function;

import javax.swing.*;
import java.awt.*;

/**
 * @author Alexander Lobas
 */
public class LightFillLayout implements LayoutManager2 {
  @Override
  public void addLayoutComponent(Component comp, Object constraints) {
  }

  @Override
  public float getLayoutAlignmentX(Container target) {
    return 0.5f;
  }

  @Override
  public float getLayoutAlignmentY(Container target) {
    return 0.5f;
  }

  @Override
  public void invalidateLayout(Container target) {
  }

  @Override
  public void addLayoutComponent(String name, Component comp) {
  }

  @Override
  public void removeLayoutComponent(Component comp) {
  }

  @Override
  public Dimension maximumLayoutSize(Container target) {
    return new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE);
  }

  @Override
  public Dimension preferredLayoutSize(Container parent) {
    return layoutSize(parent, new Function<Component, Dimension>() {
      @Override
      public Dimension fun(Component component) {
        return component.getPreferredSize();
      }
    });
  }

  @Override
  public Dimension minimumLayoutSize(Container parent) {
    return layoutSize(parent, new Function<Component, Dimension>() {
      @Override
      public Dimension fun(Component component) {
        return component.getMinimumSize();
      }
    });
  }

  private static Dimension layoutSize(Container parent, Function<Component, Dimension> getSize) {
    Component toolbar = parent.getComponent(0);
    Dimension toolbarSize = toolbar.isVisible() ? getSize.fun(toolbar) : new Dimension();
    Dimension contentSize = getSize.fun(parent.getComponent(1));
    int extraWidth = 0;
    JComponent jParent = (JComponent)parent;
    if (jParent.getClientProperty(LightToolWindow.LEFT_MIN_KEY) != null) {
      extraWidth += LightToolWindow.MINIMIZE_WIDTH;
    }
    if (jParent.getClientProperty(LightToolWindow.RIGHT_MIN_KEY) != null) {
      extraWidth += LightToolWindow.MINIMIZE_WIDTH;
    }
    return new Dimension(Math.max(toolbarSize.width, contentSize.width + extraWidth), toolbarSize.height + contentSize.height);
  }

  @Override
  public void layoutContainer(Container parent) {
    int leftWidth = 0;
    int rightWidth = 0;
    JComponent jParent = (JComponent)parent;
    JComponent left = (JComponent)jParent.getClientProperty(LightToolWindow.LEFT_MIN_KEY);
    if (left != null) {
      leftWidth = LightToolWindow.MINIMIZE_WIDTH;
    }
    JComponent right = (JComponent)jParent.getClientProperty(LightToolWindow.RIGHT_MIN_KEY);
    if (right != null) {
      rightWidth = LightToolWindow.MINIMIZE_WIDTH;
    }
    int extraWidth = leftWidth + rightWidth;

    int width = parent.getWidth() - extraWidth;
    int height = parent.getHeight();
    Component toolbar = parent.getComponent(0);
    Dimension toolbarSize = toolbar.isVisible() ? toolbar.getPreferredSize() : new Dimension();
    toolbar.setBounds(leftWidth, 0, width, toolbarSize.height);
    parent.getComponent(1).setBounds(leftWidth, toolbarSize.height, width, height - toolbarSize.height);

    if (left != null) {
      left.setBounds(0, 0, leftWidth, height);
    }
    if (right != null) {
      right.setBounds(width + leftWidth, 0, rightWidth, height);
    }
  }
}