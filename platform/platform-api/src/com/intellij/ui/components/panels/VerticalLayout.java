// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.components.panels;

import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * This class is intended to lay out added components vertically.
 * It allows to add them into the TOP, CENTER, or BOTTOM group, which are aligned separately.
 * Every group can contain any amount of components. The specified gap is added between components,
 * and the double gap is added between groups of components. The gap will be scaled automatically.
 * <p><b>NB!: this class must be modified together with the {@code HorizontalLayout} class accordingly</b></p>
 *
 * @see HorizontalLayout
 */
public final class VerticalLayout implements LayoutManager2 {
  public static final String TOP = "TOP";
  public static final String BOTTOM = "BOTTOM";
  public static final String CENTER = "CENTER";

  private final ArrayList<Component> myTop = new ArrayList<>();
  private final ArrayList<Component> myBottom = new ArrayList<>();
  private final ArrayList<Component> myCenter = new ArrayList<>();
  private final int myAlignment;
  private final int myGap;

  /**
   * Creates a layout with the specified gap.
   * All components will have preferred heights,
   * but their widths will be set according to the container.
   * The gap will be scaled automatically.
   *
   * @param gap vertical gap between components
   */
  public VerticalLayout(int gap) {
    myGap = gap;
    myAlignment = -1;
  }

  /**
   * Creates a layout with the specified gap and vertical alignment.
   * All components will have preferred sizes.
   * The gap will be scaled automatically.
   *
   * @param gap       vertical gap between components
   * @param alignment horizontal alignment for components
   *
   * @see SwingConstants#LEFT
   * @see SwingConstants#RIGHT
   * @see SwingConstants#CENTER
   */
  public VerticalLayout(int gap, int alignment) {
    myGap = gap;
    switch (alignment) {
      case SwingConstants.LEFT:
      case SwingConstants.RIGHT:
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
      if (name == null || TOP.equalsIgnoreCase(name)) {
        myTop.add(component);
      }
      else if (CENTER.equalsIgnoreCase(name)) {
        myCenter.add(component);
      }
      else if (BOTTOM.equalsIgnoreCase(name)) {
        myBottom.add(component);
      }
      else {
        throw new IllegalArgumentException("unsupported name: " + name);
      }
    }
  }

  @Override
  public void removeLayoutComponent(Component component) {
    myTop.remove(component);
    myBottom.remove(component);
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
    int gap = myGap <= 0 ? 0 : JBUI.scale(myGap);
    synchronized (container.getTreeLock()) {
      Dimension top = getPreferredSize(myTop);
      Dimension bottom = getPreferredSize(myBottom);
      Dimension center = getPreferredSize(myCenter);

      Insets insets = container.getInsets();
      int width = container.getWidth() - insets.left - insets.right;
      int height = container.getHeight() - insets.top - insets.bottom;

      int topY = 0;
      if (top != null) {
        topY = gap + layout(myTop, 0, width, insets);
      }
      int bottomY = height;
      if (bottom != null) {
        bottomY -= bottom.height;
      }
      if (bottomY < topY) {
        bottomY = topY;
      }
      if (center != null) {
        int centerY = (height - center.height) / 2;
        if (centerY > topY) {
          int centerBottomY = centerY + center.height + gap + gap;
          if (centerBottomY > bottomY) {
            centerY = bottomY - center.height - gap - gap;
          }
        }
        if (centerY < topY) {
          centerY = topY;
        }
        centerY = gap + layout(myCenter, centerY, width, insets);
        if (bottomY < centerY) {
          bottomY = centerY;
        }
      }
      if (bottom != null) {
        layout(myBottom, bottomY, width, insets);
      }
    }
  }

  private int layout(List<? extends Component> list, int y, int width, Insets insets) {
    int gap = myGap <= 0 ? 0 : JBUI.scale(myGap);
    for (Component component : list) {
      if (component.isVisible()) {
        Dimension size = component.getPreferredSize();
        int x = 0;
        if (myAlignment == -1) {
          size.width = width;
        }
        else if (myAlignment != SwingConstants.LEFT) {
          x = width - size.width;
          if (myAlignment == SwingConstants.CENTER) {
            x /= 2;
          }
        }
        component.setBounds(x + insets.left, y + insets.top, size.width, size.height);
        y += size.height + gap;
      }
    }
    return y;
  }

  private static Dimension join(Dimension result, int gap, Dimension size) {
    if (size == null) {
      return result;
    }
    if (result == null) {
      return new Dimension(size);
    }
    result.height += gap + size.height;
    if (result.width < size.width) {
      result.width = size.width;
    }
    return result;
  }

  private Dimension getPreferredSize(List<? extends Component> list) {
    int gap = myGap <= 0 ? 0 : JBUI.scale(myGap);
    Dimension result = null;
    for (Component component : list) {
      if (component.isVisible()) {
        result = join(result, gap, component.getPreferredSize());
      }
    }
    return result;
  }

  private Dimension getPreferredSize(Container container, boolean aligned) {
    int gap2 = myGap <= 0 ? 0 : 2 * JBUI.scale(myGap);
    synchronized (container.getTreeLock()) {
      Dimension top = getPreferredSize(myTop);
      Dimension bottom = getPreferredSize(myBottom);
      Dimension center = getPreferredSize(myCenter);
      Dimension result = join(join(join(null, gap2, top), gap2, center), gap2, bottom);
      if (result == null) {
        result = new Dimension();
      }
      else if (aligned && center != null) {
        int topHeight = top == null ? 0 : top.height;
        int bottomHeight = bottom == null ? 0 : bottom.height;
        result.width += Math.abs(topHeight - bottomHeight);
      }
      JBInsets.addTo(result, container.getInsets());
      return result;
    }
  }
}
