/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.diff.util;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.markup.LineMarkerRenderer;
import com.intellij.openapi.editor.markup.LineSeparatorRenderer;
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.openapi.util.BooleanGetter;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.GraphicsUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.util.Arrays;

public class DiffLineSeparatorRenderer implements LineMarkerRenderer, LineSeparatorRenderer {
  @NotNull private final Editor myEditor;
  @NotNull private final BooleanGetter myCondition;

  // TODO: adjust width to line height / HiDPI ?
  private static final int X_STEP = 4;
  private static final int Y_STEP = 4;
  private static final int Y_STEP_2 = 1;

  private static final int JOIN_DX = 4;
  private static final int JOIN_DY = 2;

  // Background, 1-2-3 top pixels, bottom pixel
  private static final Color[] LINE_COLORS = new Color[]{
    new JBColor(Gray._217, Gray._35),
    new JBColor(Gray._200, Gray._30),
    new JBColor(Gray._208, Gray._32),
    new JBColor(Gray._211, Gray._34),
    new JBColor(Gray._217, Gray._34)};

  public DiffLineSeparatorRenderer(@NotNull Editor editor) {
    this(editor, BooleanGetter.TRUE);
  }

  public DiffLineSeparatorRenderer(@NotNull Editor editor, @NotNull BooleanGetter condition) {
    myEditor = editor;
    myCondition = condition;
  }

  /*
   * Divider
   */
  public static void drawConnectorLine(@NotNull Graphics2D g,
                                       int x1, int x2,
                                       int start1, int end1,
                                       int start2, int end2) {
    int y1 = (start1 + end1) / 2;
    int y2 = (start2 + end2) / 2;

    int[] xPoints;
    int[] yPoints;

    int step = Y_STEP - Y_STEP_2;
    if (Math.abs(x2 - x1) < Math.abs(y2 - y1)) {
      if (y2 < y1) {
        xPoints = new int[]{x1, x2 - JOIN_DX, x2, x2, x1 + JOIN_DX, x1};
        yPoints = new int[]{y1 - step, y2 - step + JOIN_DY, y2 - step, y2 + step, y1 + step - JOIN_DY, y1 + step};
      }
      else {
        xPoints = new int[]{x1, x1 + JOIN_DX, x2, x2, x2 - JOIN_DX, x1};
        yPoints = new int[]{y1 - step, y1 - step + JOIN_DY, y2 - step, y2 + step, y2 + step - JOIN_DY, y1 + step};
      }
    }
    else {
      xPoints = new int[]{x1, x2, x2, x1};
      yPoints = new int[]{y1 - step, y2 - step, y2 + step, y1 + step};
    }

    paintLine(g, xPoints, yPoints);
  }

  /*
   * Gutter
   */
  @Override
  public void paint(Editor editor, Graphics g, Rectangle r) {
    if (!myCondition.get()) return;

    int y = r.y;
    final int gutterWidth = ((EditorEx)editor).getGutterComponentEx().getWidth();
    int lineHeight = myEditor.getLineHeight();

    draw(g, 0, gutterWidth, 0, y, lineHeight);
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

    final int gutterWidth = ((EditorEx)myEditor).getGutterComponentEx().getWidth();
    int lineHeight = myEditor.getLineHeight();

    draw(g, x1, x2, -gutterWidth, y, lineHeight);
  }

  private static void draw(@NotNull Graphics g,
                           int x1,
                           int x2,
                           int shiftX,
                           int shiftY,
                           int lineHeight) {
    int halfHeight = lineHeight / 2;

    int count = ((x2 - x1) / X_STEP + 3);

    int[] xPoints = new int[2 * count];
    int[] yPoints = new int[2 * count];

    int shift = Math.max(x1 - shiftX / X_STEP, 0);
    for (int index = 0; index < count; index++) {
      int absIndex = index + shift;

      int xPos = absIndex * X_STEP + shiftX;
      int yPos1;
      int yPos2;

      if (absIndex == 0) {
        yPos1 = halfHeight + shiftY - Y_STEP + Y_STEP_2;
        yPos2 = halfHeight + shiftY + Y_STEP - Y_STEP_2;
      }
      else if (absIndex % 2 == 0) {
        yPos1 = halfHeight + shiftY - Y_STEP_2;
        yPos2 = halfHeight + shiftY + Y_STEP + Y_STEP_2;
      }
      else {
        yPos1 = halfHeight + shiftY - Y_STEP - Y_STEP_2;
        yPos2 = halfHeight + shiftY + Y_STEP_2;
      }

      xPoints[index] = xPos;
      yPoints[index] = yPos1;
      xPoints[2 * count - index - 1] = xPos;
      yPoints[2 * count - index - 1] = yPos2;
    }

    GraphicsConfig config = GraphicsUtil.disableAAPainting(g);
    try {
      paintLine(g, xPoints, yPoints);
    }
    finally {
      config.restore();
    }
  }

  private static void paintLine(@NotNull Graphics g,
                                @NotNull int[] xPoints, @NotNull int[] yPoints) {
    Graphics2D gg = ((Graphics2D)g);

    // background
    gg.setColor(LINE_COLORS[0]);
    gg.fillPolygon(xPoints, yPoints, xPoints.length);
    gg.drawPolyline(xPoints, yPoints, xPoints.length);

    // shade
    AffineTransform oldTransform = gg.getTransform();

    gg.setColor(LINE_COLORS[1]);
    gg.drawPolyline(xPoints, yPoints, xPoints.length / 2);

    gg.translate(0, 1);
    gg.setColor(LINE_COLORS[2]);
    gg.drawPolyline(xPoints, yPoints, xPoints.length / 2);

    gg.translate(0, 1);
    gg.setColor(LINE_COLORS[3]);
    gg.drawPolyline(xPoints, yPoints, xPoints.length / 2);

    gg.setTransform(oldTransform);

    // bottom line
    int[] xBottomPoints = Arrays.copyOfRange(xPoints, xPoints.length / 2, xPoints.length);
    int[] yBottomPoints = Arrays.copyOfRange(yPoints, xPoints.length / 2, xPoints.length);

    gg.setColor(LINE_COLORS[4]);
    gg.drawPolyline(xBottomPoints, yBottomPoints, xPoints.length / 2);
  }
}
