package com.intellij.ui.tabs.impl;

import java.awt.*;

public class TabLayout {

  public ShapeTransform createShapeTransform(Dimension dimension) {
    return createShapeTransform(new Rectangle(0, 0, dimension.width, dimension.height));
  }

  public ShapeTransform createShapeTransform(Rectangle rectangle) {
    return new ShapeTransform.Top(rectangle);
  }
}
