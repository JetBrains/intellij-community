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

  public DiffLineSeparatorRenderer(@NotNull Editor editor) {
    myTornLine = new TornLine();
    myEditor = editor;
  }

  /*
   * Gutter
   */
  @Override
  public void paint(Editor editor, Graphics g, Rectangle r) {
    GraphicsUtil.setupAAPainting(g);

    int y = r.y;
    final int gutterWidth = ((EditorEx)editor).getGutterComponentEx().getWidth();
    final int editorWidth = editor.getScrollingModel().getVisibleArea().width;
    int lineHeight = myEditor.getLineHeight();

    myTornLine.ensureLastX(editorWidth + gutterWidth);

    TIntArrayList pointsX = myTornLine.getPointsX();
    TIntArrayList pointsY = myTornLine.getPointsY();
    g.setColor(getOuterColor());
    drawCurved(g, pointsX, pointsY, 0, gutterWidth, 0, y, lineHeight, TornLine.ourOuterRadius);
    g.setColor(getInnerColor());
    drawCurved(g, pointsX, pointsY, 0, gutterWidth, 0, y, lineHeight, TornLine.ourInnerRadius);
  }

  /*
   * Editor
   */
  @Override
  public void drawLine(Graphics g, int x1, int x2, int y) {
    GraphicsUtil.setupAAPainting(g);

    Rectangle clip = g.getClipBounds();
    x2 = clip.x + clip.width;

    final int lineWidth = x2 - x1;
    final int gutterWidth = ((EditorEx)myEditor).getGutterComponentEx().getWidth();
    int lineHeight = myEditor.getLineHeight();

    myTornLine.ensureLastX(lineWidth + gutterWidth);

    TIntArrayList pointsX = myTornLine.getPointsX();
    TIntArrayList pointsY = myTornLine.getPointsY();
    g.setColor(getOuterColor());
    drawCurved(g, pointsX, pointsY, x1, x2, -gutterWidth, y, lineHeight, TornLine.ourOuterRadius);
    g.setColor(getInnerColor());
    drawCurved(g, pointsX, pointsY, x1, x2, -gutterWidth, y, lineHeight, TornLine.ourInnerRadius);
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

    int lastX = pointsX.get(0) + shiftX;
    int lastY = pointsY.get(0) + shiftY;
    for (int i = 1; i < pointsX.size(); i++) {
      int newX = pointsX.get(i) + shiftX;
      int newY = pointsY.get(i) + shiftY;

      if (lastX > x2) break;
      if (newX >= x1) {
        UIUtil.drawLine(g, lastX, lastY - deltaY + lineHeight / 2, newX, newY - deltaY + lineHeight / 2);
        UIUtil.drawLine(g, lastX, lastY + deltaY + lineHeight / 2, newX, newY + deltaY + lineHeight / 2);
      }

      lastX = newX;
      lastY = newY;
    }
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
