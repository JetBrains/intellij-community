// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.roots.ui.componentsList.layout;

import org.jetbrains.annotations.ApiStatus;

import java.awt.*;

@ApiStatus.Internal
public final class OrientedDimensionSum {
  private final Orientation myOrientation;
  private final Dimension mySum = new Dimension();

  public OrientedDimensionSum(Orientation orientation) {
    myOrientation = orientation;
  }

  public void add(Dimension size) {
    myOrientation.expandInline(mySum, size);
  }

  public void addInsets(Insets insets) {
    mySum.width += insets.left + insets.right;
    mySum.height += insets.top + insets.bottom;
  }

  public Dimension getSum() { return mySum; }

  public void grow(int length) {
    myOrientation.extend(mySum, length);
  }

}
