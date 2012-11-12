/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.ui.tabs.impl;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;

public abstract class ShapeTransform {

  protected GeneralPath myPath = new GeneralPath();

  private Rectangle myShapeRect;

  private final int myXTransform;
  private final int myYTransform;
  private final boolean mySwap;

  protected ShapeTransform(Rectangle shapeRect, int xTransform, int yTransform, boolean swap) {
    myShapeRect = shapeRect;
    myXTransform = xTransform;
    myYTransform = yTransform;
    mySwap = swap;
  }

  protected final Rectangle getShapeRect() {
    return myShapeRect;
  }

  public abstract int getX();

  public abstract int getY();

  public abstract int getMaxX();

  public abstract int getMaxY();

  public final int deltaX(int deltaX) {
    return deltaX * myXTransform;
  }

  public final int deltaY(int deltaY) {
    return deltaY * myYTransform;
  }

  public final <T> T transformY1(T o1, T o2) {
    return (mySwap ? myXTransform : myYTransform) == 1 ? o1: o2;
  }

  public abstract Insets transformInsets(Insets insets);

  public abstract Line2D.Float transformLine(int x1, int y1, int x2, int y2);

  public abstract ShapeTransform createTransform(Rectangle innerRec);

  public abstract ShapeTransform copy();

  public final int getWidth() {
    return Math.abs(getMaxX() - getX());
  }

  public final int getHeight() {
    return Math.abs(getMaxY() - getY());
  }

  public final ShapeTransform moveTo(int x, int y) {
    if (mySwap) {
      //noinspection SuspiciousNameCombination
      myPath.moveTo(y, x);
    } else {
      myPath.moveTo(x, y);
    }

    return this;
  }

  public final ShapeTransform quadTo(int x1, int y1, int x2, int y2) {
    if (mySwap) {
      //noinspection SuspiciousNameCombination
      myPath.quadTo(y1, x1, y2, x2);
    } else {
      myPath.quadTo(x1, y1, x2, y2);
    }

    return this;
  }

  public final ShapeTransform lineTo(int x, int y) {
    if (mySwap) {
      //noinspection SuspiciousNameCombination
      myPath.lineTo(y, x);
    } else {
      myPath.lineTo(x, y);
    }

    return this;
  }

  public final GeneralPath getShape() {
    return myPath;
  }

  public final ShapeTransform reset() {
    return reset(null);
  }

  protected final ShapeTransform reset(Rectangle shapeRec) {
    myPath = new GeneralPath();
    if (shapeRec != null) {
      myShapeRect = shapeRec;
    }

    return this;
  }

  public final ShapeTransform closePath() {
    myPath.closePath();
    return this;
  }

  public final ShapeTransform doRect(int x, int y, int width, int height) {
    if (width <= 0 || height <= 0) return this;
    return moveTo(x, y).lineTo(x + deltaX(width), y).lineTo(x + deltaX(width), y + deltaY(height)).lineTo(x, y + deltaY(height)).closePath();
  }

  public static class Top extends ShapeTransform {

    public Top() {
      this(null);
    }

    public Top(Rectangle shapeRect) {
      this(shapeRect, new GeneralPath());
    }

    public Top(Rectangle shapeRect, GeneralPath path) {
      super(shapeRect, 1, 1, false);
      myPath = path;
    }

    public int getX() {
      return getShapeRect().x;
    }

    public int getY() {
      return getShapeRect().y;
    }

    public int getMaxX() {
      return (int)getShapeRect().getMaxX();
    }

    public int getMaxY() {
      return (int)getShapeRect().getMaxY();
    }


    @Override
    public ShapeTransform createTransform(Rectangle innerRec) {
      return new Top(innerRec);
    }

    @Override
    public Insets transformInsets(Insets insets) {
      return new Insets(insets.top, insets.left, insets.bottom, insets.right);
    }

    @Override
    public Line2D.Float transformLine(int x1, int y1, int x2, int y2) {
      return new Line2D.Float(x1, y1, x2, y2);
    }

    public ShapeTransform copy() {
      return new Top((Rectangle)getShapeRect().clone(), (GeneralPath)myPath.clone());
    }
  }

  public static class Left extends ShapeTransform {
    public Left() {
      this(null);
    }

    public Left(Rectangle shapeRect) {
      this(shapeRect, new GeneralPath());
    }

    public Left(Rectangle shapeRect, GeneralPath path) {
      super(shapeRect, 1, 1, true);
      myPath = path;
    }

    public int getX() {
      return getShapeRect().y;
    }

    public int getY() {
      return getShapeRect().x;
    }

    public int getMaxX() {
      return (int)getShapeRect().getMaxY();
    }

    public int getMaxY() {
      return (int)getShapeRect().getMaxX();
    }

    @Override
    public ShapeTransform createTransform(Rectangle innerRec) {
      return new Left(innerRec);
    }

    @Override
    public Line2D.Float transformLine(int x1, int y1, int x2, int y2) {
      return new Line2D.Float(y1, x1, y2, x2);
    }

    @Override
    public Insets transformInsets(Insets insets) {
      return new Insets(insets.left, insets.top, insets.right, insets.bottom);
    }

    public ShapeTransform copy() {
      return new Left((Rectangle)getShapeRect().clone(), (GeneralPath)myPath.clone());
    }
  }

  public static class Bottom extends ShapeTransform {
    public Bottom(Rectangle shapeRect, GeneralPath path) {
      super(shapeRect, 1, -1, false);
      myPath = path;
    }

    public Bottom(Rectangle shapeRect) {
      this(shapeRect, new GeneralPath());
    }

    public Bottom() {
      this(null);
    }

    public int getX() {
      return getShapeRect().x;
    }

    public int getY() {
      return (int)getShapeRect().getMaxY();
    }

    public int getMaxX() {
      return (int)getShapeRect().getMaxX();
    }

    public int getMaxY() {
      return getShapeRect().y;
    }

    public ShapeTransform copy() {
      return new Bottom((Rectangle)getShapeRect().clone(), (GeneralPath)myPath.clone());
    }

    @Override
    public ShapeTransform createTransform(Rectangle innerRec) {
      return new Bottom(innerRec);
    }

    @Override
    public Insets transformInsets(Insets insets) {
      return new Insets(insets.bottom, insets.right, insets.top, insets.left);
    }

    @Override
    public Line2D.Float transformLine(int x1, int y1, int x2, int y2) {
      return new Line2D.Float(x2,
                              Math.abs(y2),
                              x1,
                              Math.abs(y1));
    }
  }


  public static class Right extends ShapeTransform {
    public Right(Rectangle shapeRect, GeneralPath path) {
      super(shapeRect, 1, -1, true);
      myPath = path;
    }

    public Right(Rectangle rec) {
      this(rec, new GeneralPath());
    }

    public Right() {
      this(null);
    }

    public int getX() {
      return getShapeRect().y;
    }

    public int getY() {
      return (int)getShapeRect().getMaxX();
    }

    public int getMaxX() {
      return (int)getShapeRect().getMaxY();
    }

    public int getMaxY() {
      return getShapeRect().x;
    }

    public ShapeTransform copy() {
      return new Right((Rectangle)getShapeRect().clone(), (GeneralPath)myPath.clone());
    }

    @Override
    public Insets transformInsets(Insets insets) {
      return new Insets(insets.right, insets.top, insets.left, insets.bottom);
    }

    @Override
    public ShapeTransform createTransform(Rectangle innerRec) {
      return new Right(innerRec);
    }

    public Line2D.Float transformLine(int x1, int y1, int x2, int y2) {
      return new Line2D.Float(y1, x1, y2, x2);
    }
  }


}
