/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ui;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;

public class LineEndDecorator {

  private static int myArrowSize = 9;
  private static Shape myArrowPolygon = new Polygon(new int[] {0, myArrowSize, 0, 0}, new int[] {0, myArrowSize /2, myArrowSize, 0}, 4);

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
