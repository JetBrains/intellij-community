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
package com.intellij.openapi.util.diff.util;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.markup.LineMarkerRenderer;
import com.intellij.openapi.editor.markup.LineSeparatorRenderer;
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.UIUtil;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Random;

public class DiffLineSeparatorRenderer implements LineMarkerRenderer, LineSeparatorRenderer {
  @NotNull private final TornLine myTornLine;
  @NotNull private final Editor myEditor;
  @NotNull private final BooleanGetter myCondition;

  public DiffLineSeparatorRenderer(@NotNull Editor editor) {
    this(editor, BooleanGetter.TRUE);
  }

  public DiffLineSeparatorRenderer(@NotNull Editor editor, @NotNull BooleanGetter condition) {
    myTornLine = new TornLine();
    myEditor = editor;
    myCondition = condition;
  }

  /*
   * Divider
   */
  public static void drawSimpleConnectorLine(@NotNull Graphics2D g,
                                             int x1, int x2,
                                             int start1, int end1,
                                             int start2, int end2) {
    int y1 = (start1 + end1) / 2;
    int y2 = (start2 + end2) / 2;

    if (Math.abs(x2 - x1) < Math.abs(y2 - y1)) {
      int dx = TornLine.ourOuterRadius;
      int dy = TornLine.ourInnerRadius;
      if (y2 < y1) {
        g.setColor(getOuterColor());
        g.drawLine(x1 + dx, y1 - dy + TornLine.ourOuterRadius, x2, y2 + TornLine.ourOuterRadius);
        g.drawLine(x1, y1 - TornLine.ourOuterRadius, x2 - dx, y2 + dy - TornLine.ourOuterRadius);

        g.drawLine(x1, y1 + TornLine.ourOuterRadius, x1 + dx, y1 - dy + TornLine.ourOuterRadius);
        g.drawLine(x2, y2 - TornLine.ourOuterRadius, x2 - dx, y2 + dy - TornLine.ourOuterRadius);

        g.setColor(getInnerColor());
        g.drawLine(x1 + dx, y1 - dy + TornLine.ourInnerRadius, x2, y2 + TornLine.ourInnerRadius);
        g.drawLine(x1, y1 - TornLine.ourInnerRadius, x2 - dx, y2 + dy - TornLine.ourInnerRadius);

        g.drawLine(x1, y1 + TornLine.ourInnerRadius, x1 + dx, y1 - dy + TornLine.ourInnerRadius);
        g.drawLine(x2, y2 - TornLine.ourInnerRadius, x2 - dx, y2 + dy - TornLine.ourInnerRadius);
      }
      else {
        g.setColor(getOuterColor());
        g.drawLine(x1, y1 + TornLine.ourOuterRadius, x2 - dx, y2 - dy + TornLine.ourOuterRadius);
        g.drawLine(x1 + dx, y1 + dy - TornLine.ourOuterRadius, x2, y2 - TornLine.ourOuterRadius);

        g.drawLine(x2, y2 + TornLine.ourOuterRadius, x2 - dx, y2 - dy + TornLine.ourOuterRadius);
        g.drawLine(x1, y1 - TornLine.ourOuterRadius, x1 + dx, y1 + dy - TornLine.ourOuterRadius);

        g.setColor(getInnerColor());
        g.drawLine(x1, y1 + TornLine.ourInnerRadius, x2 - dx, y2 - dy + TornLine.ourInnerRadius);
        g.drawLine(x1 + dx, y1 + dy - TornLine.ourInnerRadius, x2, y2 - TornLine.ourInnerRadius);

        g.drawLine(x2, y2 + TornLine.ourInnerRadius, x2 - dx, y2 - dy + TornLine.ourInnerRadius);
        g.drawLine(x1, y1 - TornLine.ourInnerRadius, x1 + dx, y1 + dy - TornLine.ourInnerRadius);
      }
    }
    else {
      g.setColor(getOuterColor());
      UIUtil.drawLine(g, x1, y1 - TornLine.ourOuterRadius, x2, y2 - TornLine.ourOuterRadius);
      UIUtil.drawLine(g, x1, y1 + TornLine.ourOuterRadius, x2, y2 + TornLine.ourOuterRadius);

      g.setColor(getInnerColor());
      UIUtil.drawLine(g, x1, y1 - TornLine.ourInnerRadius, x2, y2 - TornLine.ourInnerRadius);
      UIUtil.drawLine(g, x1, y1 + TornLine.ourInnerRadius, x2, y2 + TornLine.ourInnerRadius);
    }
  }

  public static void drawConnectorLine(@NotNull Graphics2D g,
                                       int x1, int x2,
                                       int start1, int end1,
                                       int start2, int end2) {
    int y1 = (start1 + end1) / 2;
    int y2 = (start2 + end2) / 2;

    DiffDrawUtil.drawCurveTrapezium(g, x1, x2,
                                    y1 - TornLine.ourOuterRadius, y1 + TornLine.ourOuterRadius,
                                    y2 - TornLine.ourOuterRadius, y2 + TornLine.ourOuterRadius,
                                    null, getOuterColor());

    DiffDrawUtil.drawCurveTrapezium(g, x1, x2,
                                    y1 - TornLine.ourInnerRadius, y1 + TornLine.ourInnerRadius,
                                    y2 - TornLine.ourInnerRadius, y2 + TornLine.ourInnerRadius,
                                    null, getInnerColor());
  }

  /*
   * Gutter
   */
  @Override
  public void paint(Editor editor, Graphics g, Rectangle r) {
    if (!myCondition.get()) return;

    // FIXME: painting of hundreds of AA lines lead to heavy UI slowdown. Each line is drew by ~(DISPLAY_WIDTH / 2) lines.
    // TODO: We should paint somehow else, faster. Dotlines?

    int y = r.y;
    final int gutterWidth = ((EditorEx)editor).getGutterComponentEx().getWidth();
    final int editorWidth = editor.getScrollingModel().getVisibleArea().width;
    int lineHeight = myEditor.getLineHeight();

    myTornLine.ensureLastX(editorWidth + gutterWidth);
    TIntArrayList pointsX = myTornLine.getPointsX();
    TIntArrayList pointsY = myTornLine.getPointsY();

    GraphicsConfig config = GraphicsUtil.setupAAPainting(g);

    g.setColor(getOuterColor());
    drawCurved(g, pointsX, pointsY, 0, gutterWidth, 0, y, lineHeight, TornLine.ourOuterRadius);
    g.setColor(getInnerColor());
    drawCurved(g, pointsX, pointsY, 0, gutterWidth, 0, y, lineHeight, TornLine.ourInnerRadius);

    config.restore();
  }

  /*
   * Editor
   */
  @Override
  public void drawLine(Graphics g, int x1, int x2, int y) {
    if (!myCondition.get()) return;

    y++; // we want y to be line's top position

    Rectangle clip = g.getClipBounds();
    x2 = clip.x + clip.width;

    final int lineWidth = x2 - x1;
    final int gutterWidth = ((EditorEx)myEditor).getGutterComponentEx().getWidth();
    int lineHeight = myEditor.getLineHeight();

    myTornLine.ensureLastX(lineWidth + gutterWidth);
    TIntArrayList pointsX = myTornLine.getPointsX();
    TIntArrayList pointsY = myTornLine.getPointsY();

    GraphicsConfig config = GraphicsUtil.setupAAPainting(g);

    g.setColor(getOuterColor());
    drawCurved(g, pointsX, pointsY, x1, x2, -gutterWidth, y, lineHeight, TornLine.ourOuterRadius);
    g.setColor(getInnerColor());
    drawCurved(g, pointsX, pointsY, x1, x2, -gutterWidth, y, lineHeight, TornLine.ourInnerRadius);

    config.restore();
  }

  private static void drawCurved(@NotNull Graphics g,
                                 @NotNull TIntArrayList pointsX,
                                 @NotNull TIntArrayList pointsY,
                                 int x1,
                                 int x2,
                                 int shiftX,
                                 int shiftY,
                                 int lineHeight,
                                 int deltaY) {
    assert pointsX.size() == pointsY.size();

    int halfHeight = lineHeight / 2;
    int maxPointNumber = Math.min((x2 - x1) / (TornLine.ourMinDeltaX) + 3, pointsX.size());

    int[] xPoints = new int[maxPointNumber];
    int[] yPoints1 = new int[maxPointNumber];
    int[] yPoints2 = new int[maxPointNumber];
    int n = 0;

    int lastX = pointsX.get(0) + shiftX;
    int lastY = pointsY.get(0) + shiftY;
    for (int i = 1; i < pointsX.size(); i++) {
      int newX = pointsX.get(i) + shiftX;
      int newY = pointsY.get(i) + shiftY;

      if (newX >= x1) {
        xPoints[n] = lastX;
        yPoints1[n] = lastY - deltaY + halfHeight;
        yPoints2[n] = lastY + deltaY + halfHeight;
        n++;
      }
      if (lastX > x2) break;

      lastX = newX;
      lastY = newY;

      if (i == pointsX.size() - 1) {
        xPoints[n] = lastX;
        yPoints1[n] = lastY - deltaY + halfHeight;
        yPoints2[n] = lastY + deltaY + halfHeight;
        n++;
      }
    }

    g.drawPolyline(xPoints, yPoints1, n);
    g.drawPolyline(xPoints, yPoints2, n);
  }

  public static class TornLine {
    public static final int ourOuterRadius = 3;
    public static final int ourInnerRadius = 2;
    public static final int ourMinDeltaX = 4;
    public static final int ourDeltaX = 7;
    public static final int ourDeltaY = 2;

    @NotNull private final TIntArrayList myPointsX;
    @NotNull private final TIntArrayList myPointsY;

    private TornLine() {
      myPointsX = new TIntArrayList();
      myPointsY = new TIntArrayList();
    }

    public void ensureLastX(int x) {
      if (myPointsX.isEmpty() || myPointsX.get(myPointsX.size() - 1) < x) {
        if (myPointsX.isEmpty()) {
          myPointsX.add(0);
          myPointsY.add(0);
        }
        generateLine(myPointsX, myPointsY, x, ourMinDeltaX, ourDeltaX, ourDeltaY);
      }
    }

    @NotNull
    public TIntArrayList getPointsX() {
      return myPointsX;
    }

    @NotNull
    public TIntArrayList getPointsY() {
      return myPointsY;
    }
  }

  private static void generateLine(@NotNull TIntArrayList pointsX, @NotNull TIntArrayList pointsY, int finalX,
                                   int minDeltaX, int deltaX, int deltaY) {
    int currentX = pointsX.get(pointsX.size() - 1);
    int currentY = pointsY.get(pointsY.size() - 1);

    Random rng = new Random();

    while (currentX < finalX) {
      int newY = currentY;
      while (newY == currentY) {
        newY = rng.nextInt(deltaY * 2) - deltaY;
      }
      int newX = currentX + minDeltaX + rng.nextInt(deltaX);
      newX = Math.min(newX, finalX);

      pointsX.add(newX);
      pointsY.add(newY);

      currentX = newX;
      currentY = newY;
    }
  }

  @NotNull
  public static Color getOuterColor() {
    final Color borderColor = JBColor.border();
    return new Color(borderColor.getRed() + 10, borderColor.getGreen() + 10, borderColor.getBlue() + 10);
  }

  @NotNull
  public static Color getInnerColor() {
    return getOuterColor().darker();
  }
}
