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
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.markup.LineMarkerRenderer;
import com.intellij.openapi.editor.markup.LineSeparatorRenderer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public class DiffLineSeparatorRenderer implements LineMarkerRenderer, LineSeparatorRenderer {
  @NotNull private final Editor myEditor;
  @NotNull private final BooleanGetter myCondition;

  // TODO: adjust width to line height?
  private static final int X_STEP = 4;
  private static final int Y_STEP = 4;
  private static final int Y_STEP_2 = 2;

  public DiffLineSeparatorRenderer(@NotNull Editor editor) {
    this(editor, BooleanGetter.TRUE);
  }

  public DiffLineSeparatorRenderer(@NotNull Editor editor, @NotNull BooleanGetter condition) {
    myEditor = editor;
    myCondition = condition;
  }

  public static void drawConnectorLine(@NotNull Graphics2D g,
                                       int x1, int x2,
                                       int start1, int end1,
                                       int start2, int end2) {
    int y1 = (start1 + end1) / 2;
    int y2 = (start2 + end2) / 2;

    DiffDrawUtil.drawCurveTrapezium(g, x1, x2,
                                    y1 - Y_STEP, y1 + Y_STEP,
                                    y2 - Y_STEP, y2 + Y_STEP,
                                    getInnerColor(), getOuterColor());
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

    // TODO: disable AA
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
    Graphics gg = g.create(0, 0, x2 - x1, lineHeight);
    gg.translate(shiftX, shiftY);
    try {
      int halfHeight = lineHeight / 2;

      int count = ((x2 - x1) / X_STEP + 3) * 2;

      int[] xPoints = new int[count];
      int[] yPoints = new int[count];

      int shift = Math.max(x1 - shiftX / X_STEP, 0);
      for (int index = 0; index * 2 < count; index++) {
        int absIndex = index + shift;

        int xPos = absIndex * X_STEP + shiftX;
        int yPos1;
        int yPos2;

        if (absIndex == 0) {
          yPos1 = halfHeight + shiftY - Y_STEP;
          yPos2 = halfHeight + shiftY + Y_STEP;
        }
        else if (absIndex % 2 == 0) {
          yPos1 = halfHeight + shiftY - Y_STEP_2;
          yPos2 = halfHeight + shiftY + Y_STEP + Y_STEP_2;
        }
        else {
          yPos1 = halfHeight + shiftY - Y_STEP - Y_STEP_2;
          yPos2 = halfHeight + shiftY + Y_STEP_2;
        }

        setPointPair(xPoints, yPoints, index, count, xPos, yPos1, yPos2);
      }

      Color innerColor = getInnerColor();
      Color outerColor = getOuterColor();

      if (innerColor != null) {
        g.setColor(innerColor);
        g.fillPolygon(xPoints, yPoints, count);
      }
      if (outerColor != null) {
        g.setColor(outerColor);
        g.drawPolyline(xPoints, yPoints, count / 2);
        for (int i = 0; i * 2 < count; i++) {
          xPoints[i] = xPoints[count - i - 1];
          yPoints[i] = yPoints[count - i - 1];
        }
        g.drawPolyline(xPoints, yPoints, count / 2);
      }
    }
    finally {
      gg.dispose();
    }
  }

  private static void setPointPair(@NotNull int[] xPoints, @NotNull int[] yPoints,
                                   int index, int count,
                                   int x, int y1, int y2) {
    xPoints[index] = x;
    xPoints[count - index - 1] = x;
    yPoints[index] = y1;
    yPoints[count - index - 1] = y2;
  }

  @Nullable
  public static Color getOuterColor() {
    return EditorColorsManager.getInstance().getGlobalScheme().getColor(EditorColors.TEARLINE_COLOR);
  }

  @Nullable
  public static Color getInnerColor() {
    return EditorColorsManager.getInstance().getGlobalScheme().getColor(EditorColors.GUTTER_BACKGROUND);
  }
}
