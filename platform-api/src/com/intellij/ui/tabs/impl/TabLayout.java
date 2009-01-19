package com.intellij.ui.tabs.impl;

import java.awt.*;

public class TabLayout {

  public ShapeTransform createShapeTransform(Rectangle rectangle) {
    return new ShapeTransform.Top(rectangle);
  }
}
