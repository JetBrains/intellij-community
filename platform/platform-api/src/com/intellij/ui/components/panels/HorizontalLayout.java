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
package com.intellij.ui.components.panels;

import com.intellij.util.ui.JBInsets;

import javax.swing.SwingConstants;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager2;
import java.util.ArrayList;

/**
 * This class is intended to lay out added components horizontally.
 * It allows to add them into the LEFT, CENTER, or RIGHT group, which are aligned separately.
 * Every group can contain any amount of components. The specified gap is added between components,
 * and the double gap is added between groups of components.
 * <p><b>NB!: this class must be modified together with the {@code VerticalLayout} class accordingly</b></p>
 *
 * @author Sergey.Malenkov
 * @see VerticalLayout
 */
public final class HorizontalLayout implements LayoutManager2 {
  public static final String LEFT = "LEFT";
  public static final String RIGHT = "RIGHT";
  public static final String CENTER = "CENTER";

  private final ArrayList<Component> myLeft = new ArrayList<>();
  private final ArrayList<Component> myRight = new ArrayList<>();
  private final ArrayList<Component> myCenter = new ArrayList<>();
  private final int myAlignment;
  private final int myGap;

  /**
   * Creates a layout with the specified gap.
   * All components will have preferred widths,
   * but their heights will be set according to the container.
   *
   * @param gap horizontal gap between components
   */
  public HorizontalLayout(int gap) {
    myGap = gap;
    myAlignment = -1;
  }

  /**
   * Creates a layout with the specified gap and vertical alignment.
   * All components will have preferred sizes.
   *
   * @param gap       horizontal gap between components
   * @param alignment vertical alignment for components
   *
   * @see SwingConstants#TOP
   * @see SwingConstants#BOTTOM
   * @see SwingConstants#CENTER
   */
  public HorizontalLayout(int gap, int alignment) {
    myGap = gap;
    switch (alignment) {
      case SwingConstants.TOP:
      case SwingConstants.BOTTOM:
      case SwingConstants.CENTER:
        myAlignment = alignment;
        break;
      default:
        throw new IllegalArgumentException("unsupported alignment: " + alignment);
    }
  }

  @Override
  public void addLayoutComponent(Component component, Object constraints) {
    if ((constraints == null) || (constraints instanceof String)) {
      addLayoutComponent((String)constraints, component);
    }
    else {
      throw new IllegalArgumentException("unsupported constraints: " + constraints);
    }
  }

  @Override
  public Dimension maximumLayoutSize(Container target) {
    return new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE);
  }

  @Override
  public float getLayoutAlignmentX(Container target) {
    return .5f;
  }

  @Override
  public float getLayoutAlignmentY(Container target) {
    return .5f;
  }

  @Override
  public void invalidateLayout(Container target) {
  }

  @Override
  public void addLayoutComponent(String name, Component component) {
    synchronized (component.getTreeLock()) {
      if (name == null || LEFT.equalsIgnoreCase(name)) {
        myLeft.add(component);
      }
      else if (CENTER.equalsIgnoreCase(name)) {
        myCenter.add(component);
      }
      else if (RIGHT.equalsIgnoreCase(name)) {
        myRight.add(component);
      }
      else {
        throw new IllegalArgumentException("unsupported name: " + name);
      }
    }
  }

  @Override
  public void removeLayoutComponent(Component component) {
    myLeft.remove(component);
    myRight.remove(component);
    myCenter.remove(component);
  }

  @Override
  public Dimension preferredLayoutSize(Container container) {
    return getPreferredSize(container, true);
  }

  @Override
  public Dimension minimumLayoutSize(Container container) {
    return getPreferredSize(container, false);
  }

  @Override
  public void layoutContainer(Container container) {
    synchronized (container.getTreeLock()) {
      Dimension left = getPreferredSize(myLeft);
      Dimension right = getPreferredSize(myRight);
      Dimension center = getPreferredSize(myCenter);

      Insets insets = container.getInsets();
      int width = container.getWidth() - insets.left - insets.right;
      int height = container.getHeight() - insets.top - insets.bottom;

      int leftX = 0;
      if (left != null) {
        leftX = myGap + layout(myLeft, 0, height, insets);
      }
      int rightX = width;
      if (right != null) {
        rightX -= right.width;
      }
      if (rightX < leftX) {
        rightX = leftX;
      }
      if (center != null) {
        int centerX = (width - center.width) / 2;
        if (centerX > leftX) {
          int centerRightX = centerX + center.width + myGap + myGap;
          if (centerRightX > rightX) {
            centerX = rightX - center.width - myGap - myGap;
          }
        }
        if (centerX < leftX) {
          centerX = leftX;
        }
        centerX = myGap + layout(myCenter, centerX, height, insets);
        if (rightX < centerX) {
          rightX = centerX;
        }
      }
      if (right != null) {
        layout(myRight, rightX, height, insets);
      }
    }
  }

  private int layout(ArrayList<Component> list, int x, int height, Insets insets) {
    for (Component component : list) {
      if (component.isVisible()) {
        Dimension size = component.getPreferredSize();
        int y = 0;
        if (myAlignment == -1) {
          size.height = height;
        }
        else if (myAlignment != SwingConstants.TOP) {
          y = height - size.height;
          if (myAlignment == SwingConstants.CENTER) {
            y /= 2;
          }
        }
        component.setBounds(x + insets.left, y + insets.top, size.width, size.height);
        x += size.width + myGap;
      }
    }
    return x;
  }

  private static Dimension join(Dimension result, int gap, Dimension size) {
    if (size == null) {
      return result;
    }
    if (result == null) {
      return new Dimension(size);
    }
    result.width += gap + size.width;
    if (result.height < size.height) {
      result.height = size.height;
    }
    return result;
  }

  private Dimension getPreferredSize(ArrayList<Component> list) {
    Dimension result = null;
    for (Component component : list) {
      if (component.isVisible()) {
        result = join(result, myGap, component.getPreferredSize());
      }
    }
    return result;
  }

  private Dimension getPreferredSize(Container container, boolean aligned) {
    synchronized (container.getTreeLock()) {
      Dimension left = getPreferredSize(myLeft);
      Dimension right = getPreferredSize(myRight);
      Dimension center = getPreferredSize(myCenter);
      Dimension result = join(join(join(null, myGap + myGap, left), myGap + myGap, center), myGap + myGap, right);
      if (result == null) {
        result = new Dimension();
      }
      else if (aligned && center != null) {
        int leftWidth = left == null ? 0 : left.width;
        int rightWidth = right == null ? 0 : right.width;
        result.width += Math.abs(leftWidth - rightWidth);
      }
      JBInsets.addTo(result, container.getInsets());
      return result;
    }
  }
}
