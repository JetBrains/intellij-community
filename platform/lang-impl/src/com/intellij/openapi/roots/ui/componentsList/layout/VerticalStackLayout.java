// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.roots.ui.componentsList.layout;


import org.jetbrains.annotations.ApiStatus;

import java.awt.*;

public final class VerticalStackLayout implements LayoutManager2 {
  private static final int myDefaultHeight = 200;

  /**
   * Calculates the minimum size dimensions for the specified
   * container, given the components it contains.
   * @param parent the component to be laid out
   * @see #preferredLayoutSize
   */
  @Override
  public Dimension minimumLayoutSize(Container parent) {
    ComponentOperation.SizeCalculator calculator = new ComponentOperation.SizeCalculator(SizeProperty.MINIMUM_SIZE);
    withAllVisibleDo(parent, calculator);
    OrientedDimensionSum result = calculator.getSum();
    result.addInsets(parent.getInsets());
    return result.getSum();
  }

  @ApiStatus.Internal
  public static void withAllVisibleDo(Container container, ComponentOperation operation) {
    Component[] components = container.getComponents();
    for (Component component : components) {
      if (!component.isVisible()) continue;
      operation.applyTo(component);
    }
  }

  /**
   * Lays out the specified container.
   * @param parent the container to be laid out
   */
  @Override
  public void layoutContainer(final Container parent) {
    withAllVisibleDo(parent,
                     new ComponentOperation.InlineLayout(parent, myDefaultHeight, SizeProperty.PREFERED_SIZE,
                                                         Orientation.VERTICAL));
  }

  /**
   * Calculates the preferred size dimensions for the specified
   * container, given the components it contains.
   * @param parent the container to be laid out
   *
   * @see #minimumLayoutSize
   */
  @Override
  public Dimension preferredLayoutSize(Container parent) {
    ComponentOperation.SizeCalculator calculator =
        new ComponentOperation.SizeCalculator(myDefaultHeight, SizeProperty.PREFERED_SIZE, Orientation.VERTICAL);
    withAllVisibleDo(parent, calculator);
    OrientedDimensionSum result = calculator.getSum();
    result.addInsets(parent.getInsets());
    return result.getSum();
  }

  @Override
  public void removeLayoutComponent(Component comp) {}
  @Override
  public Dimension maximumLayoutSize(Container target) { return null; }
  @Override
  public float getLayoutAlignmentY(Container target) { return 0; }
  @Override
  public void addLayoutComponent(Component comp, Object constraints) {}
  @Override
  public void invalidateLayout(Container target) {}
  @Override
  public void addLayoutComponent(String name, Component comp) {}
  @Override
  public float getLayoutAlignmentX(Container target) { return 0; }
}
