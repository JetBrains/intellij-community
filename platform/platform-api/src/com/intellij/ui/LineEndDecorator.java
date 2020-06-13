// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;

public final class LineEndDecorator {

  private static final int myArrowSize = 9;
  private static final Shape myArrowPolygon = new Polygon(new int[] {0, myArrowSize, 0, 0}, new int[] {0, myArrowSize /2, myArrowSize, 0}, 4);

  public static Shape getArrowShape(Line2D line, Point2D intersectionPoint) {
    final double deltaY = line.getP2().getY() - line.getP1().getY();
    final double length = Math.sqrt(Math.pow(deltaY, 2) + Math.pow(line.getP2().getX() - line.getP1().getX(), 2));

    double theta = Math.asin(deltaY / length);

    if (line.getP1().getX() > line.getP2().getX()) {
      theta = Math.PI - theta;
    }

    AffineTransform rotate = AffineTransform.getRotateInstance(theta, myArrowSize, myArrowSize / 2);
    Shape polygon = rotate.createTransformedShape(myArrowPolygon);

    AffineTransform move = AffineTransform.getTranslateInstance(intersectionPoint.getX() - myArrowSize, intersectionPoint.getY() - myArrowSize /2);
    polygon = move.createTransformedShape(polygon);
    return polygon;
  }

}
