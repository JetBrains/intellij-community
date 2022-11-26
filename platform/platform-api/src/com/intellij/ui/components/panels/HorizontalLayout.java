// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.components.panels;

import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBValue;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * This class is intended to lay out added components horizontally.
 * It allows to add them into the LEFT, CENTER, or RIGHT group, which are aligned separately.
 * Every group can contain any amount of components. The specified gap is added between components,
 * and the double gap is added between groups of components. The gap will be scaled automatically.
 * <p><b>NB!: this class must be modified together with the {@code VerticalLayout} class accordingly</b></p>
 *
 * @see VerticalLayout
 */
public final class HorizontalLayout implements LayoutManager2 {
  public static final int FILL = -1;
  public static final String LEFT = "LEFT";
  public static final String RIGHT = "RIGHT";
  public static final String CENTER = "CENTER";

  private final ArrayList<Component> myLeft = new ArrayList<>();
  private final ArrayList<Component> myRight = new ArrayList<>();
  private final ArrayList<Component> myCenter = new ArrayList<>();
  private final int myAlignment;
  private final JBValue myGap;

  /**
   * Creates a layout with the specified gap.
   * All components will have preferred widths,
   * but their heights will be set according to the container.
   * The gap will be scaled automatically.
   *
   * @param gap horizontal gap between components, without DPI scaling
   */
  public HorizontalLayout(int gap) {
    this(gap, FILL);
  }

  /**
   * Creates a layout with the specified gap and vertical alignment.
   * All components will have preferred sizes.
   * The gap will be scaled automatically.
   *
   * @param gap       horizontal gap between components, without DPI scaling
   * @param alignment vertical alignment for components
   * @see SwingConstants#TOP
   * @see SwingConstants#BOTTOM
   * @see SwingConstants#CENTER
   */
  public HorizontalLayout(int gap, int alignment) {
    this(new JBValue.Float(Math.max(0, gap)), alignment);
  }

  public HorizontalLayout(@NotNull JBValue gap, int alignment) {
    myGap = gap;
    switch (alignment) {
      case FILL, SwingConstants.TOP, SwingConstants.BOTTOM, SwingConstants.CENTER -> myAlignment = alignment;
      default -> throw new IllegalArgumentException("unsupported alignment: " + alignment);
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
    int gap = myGap.get();
    synchronized (container.getTreeLock()) {
      Dimension left = getPreferredSize(myLeft);
      Dimension right = getPreferredSize(myRight);
      Dimension center = getPreferredSize(myCenter);

      Insets insets = container.getInsets();
      int width = container.getWidth() - insets.left - insets.right;
      int height = container.getHeight() - insets.top - insets.bottom;

      int leftX = 0;
      if (left != null) {
        leftX = gap + layout(myLeft, 0, height, insets);
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
          int centerRightX = centerX + center.width + gap + gap;
          if (centerRightX > rightX) {
            centerX = rightX - center.width - gap - gap;
          }
        }
        if (centerX < leftX) {
          centerX = leftX;
        }
        centerX = gap + layout(myCenter, centerX, height, insets);
        if (rightX < centerX) {
          rightX = centerX;
        }
      }
      if (right != null) {
        layout(myRight, rightX, height, insets);
      }
    }
  }

  private int layout(List<? extends Component> list, int x, int height, Insets insets) {
    int gap = myGap.get();
    for (Component component : list) {
      if (component.isVisible()) {
        Dimension size = LayoutUtil.getPreferredSize(component);
        int y = 0;
        if (myAlignment == FILL) {
          size.height = height;
        }
        else if (myAlignment != SwingConstants.TOP) {
          y = height - size.height;
          if (myAlignment == SwingConstants.CENTER) {
            y /= 2;
          }
        }
        component.setBounds(x + insets.left, y + insets.top, size.width, size.height);
        x += size.width + gap;
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

  private Dimension getPreferredSize(List<? extends Component> list) {
    int gap = myGap.get();
    Dimension result = null;
    for (Component component : list) {
      if (component.isVisible()) {
        result = join(result, gap, LayoutUtil.getPreferredSize(component));
      }
    }
    return result;
  }

  private Dimension getPreferredSize(Container container, boolean aligned) {
    int gap2 = 2 * myGap.get();
    synchronized (container.getTreeLock()) {
      Dimension left = getPreferredSize(myLeft);
      Dimension right = getPreferredSize(myRight);
      Dimension center = getPreferredSize(myCenter);
      Dimension result = join(join(join(null, gap2, left), gap2, center), gap2, right);
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

  @NotNull
  public List<? extends Component> getComponents() {
    return ContainerUtil.concat(myLeft, myCenter, myRight);
  }
}
