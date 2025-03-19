// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.roots.ui.componentsList.layout;


import org.jetbrains.annotations.ApiStatus;

import java.awt.*;

@ApiStatus.Internal
public abstract class ComponentOperation {
  public abstract void applyTo(Component component);

  public static final class SizeCalculator extends ComponentOperation {
    private final int myDefaultExtent;
    private final SizeProperty mySizeProperty;
    private final OrientedDimensionSum myDimensionSum;

    public SizeCalculator(int defaultExtent, SizeProperty sizeProperty, Orientation orientation) {
      myDefaultExtent = defaultExtent;
      mySizeProperty = sizeProperty;
      myDimensionSum = new OrientedDimensionSum(orientation);
    }

    SizeCalculator(SizeProperty sizeProperty) {
      this(0, sizeProperty, Orientation.VERTICAL);
    }

    @Override
    public void applyTo(Component component) {
      Dimension size = mySizeProperty.getSize(component);
      if (size != null) {
        myDimensionSum.add(size);
      } else
        myDimensionSum.grow(myDefaultExtent);
    }

    public OrientedDimensionSum getSum() {
      return myDimensionSum;
    }
  }

  public static final class InlineLayout extends ComponentOperation {
    private final Point myPosition;
    private final int myParentExtent;
    private final int myDefaultExtent;
    private final SizeProperty mySizeProperty;
    private final Orientation myOrientation;

    public InlineLayout(Container parent, int defaultExtent, SizeProperty sizeProperty, Orientation orientation) {
      final Insets insets = parent.getInsets();

      myOrientation = orientation;
      mySizeProperty = sizeProperty;
      myDefaultExtent = defaultExtent;
      myParentExtent = myOrientation.getContrary().getInnerExtent(parent);
      myPosition = new Point(insets.left, insets.top);
    }

    @Override
    public void applyTo(Component component) {
      component.setSize(myParentExtent, myDefaultExtent);
      Dimension preferredSize = mySizeProperty.getSize(component);
      int height = getHeight(preferredSize);
      int width = getWidth(preferredSize);
      component.setBounds(myPosition.x, myPosition.y, width, height);
      myOrientation.advance(myPosition, width, height);
    }

    private int getHeight(Dimension preferredSize) {
      if (myOrientation.isVertical())
        return preferredSize != null ? preferredSize.height : myDefaultExtent;
      else
        return myParentExtent;
    }

    private int getWidth(Dimension preferredSize) {
      if (!myOrientation.isVertical())
        return preferredSize != null ? preferredSize.width : myDefaultExtent;
      else
        return myParentExtent;
    }
  }

}
