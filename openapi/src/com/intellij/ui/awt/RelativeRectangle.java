/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ui.awt;

import javax.swing.*;
import java.awt.*;

public class RelativeRectangle {

  private RelativePoint myPoint;
  private Dimension myDimension;

  public RelativeRectangle() {
    this(new RelativePoint(new JLabel(), new Point()), new Dimension());
  }

  public RelativeRectangle(JComponent component) {
    this(new RelativePoint(component.getParent(), component.getBounds().getLocation()), component.getBounds().getSize());
  }

  public RelativeRectangle(Component component, Rectangle rectangle) {
    this(new RelativePoint(component, rectangle.getLocation()), rectangle.getSize());
  }

  public RelativeRectangle(RelativePoint point, Dimension dimension) {
    myDimension = dimension;
    myPoint = point;
  }

  public Dimension getDimension() {
    return myDimension;
  }

  public RelativePoint getPoint() {
    return myPoint;
  }

  public RelativePoint getMaxPoint() {
    return new RelativePoint(myPoint.getComponent(),
        new Point(myPoint.getPoint().x + myDimension.width, myPoint.getPoint().y + myDimension.height));
  }

  public Rectangle getRectangleOn(Component target) {
    return new Rectangle(getPoint().getPoint(target), getDimension());
  }

  public Rectangle getScreenRectangle() {
    return new Rectangle(getPoint().getScreenPoint(), getDimension());
  }

  public static RelativeRectangle fromScreen(JComponent target, Rectangle screenRectangle) {
    Point relativePoint = screenRectangle.getLocation();
    SwingUtilities.convertPointFromScreen(relativePoint, target);
    return new RelativeRectangle(new RelativePoint(target, relativePoint), screenRectangle.getSize());
  }

  public Component getComponent() {
    return getPoint().getComponent();
  }
}
