/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ui;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;

public class LineEndDecorator {

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
