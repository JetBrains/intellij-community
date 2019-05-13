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
import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import com.intellij.openapi.editor.markup.LineMarkerRendererEx;
import com.intellij.openapi.editor.markup.LineSeparatorRenderer;
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.openapi.util.BooleanGetter;
import com.intellij.ui.Gray;
import com.intellij.util.ui.GraphicsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.geom.AffineTransform;

public class DiffLineSeparatorRenderer implements LineMarkerRendererEx, LineSeparatorRenderer {
  @NotNull private final Editor myEditor;
  @NotNull private final BooleanGetter myCondition;

  public DiffLineSeparatorRenderer(@NotNull Editor editor, @NotNull BooleanGetter condition) {
    myEditor = editor;
    myCondition = condition;
  }

  /*
   * Divider
   */
  public static void drawConnectorLine(@NotNull Graphics2D g,
                                       int x1, int x2,
                                       int y1, int y2,
                                       int lineHeight,
                                       @Nullable EditorColorsScheme scheme) {
    int step = getStepSize(lineHeight);
    int height = getHeight(lineHeight);
    if (scheme == null) scheme = EditorColorsManager.getInstance().getGlobalScheme();

    int start1 = y1 + (lineHeight - height - step) / 2 + step / 2;
    int start2 = y2 + (lineHeight - height - step) / 2 + step / 2;
    int end1 = start1 + height - 1;
    int end2 = start2 + height - 1;

    Color color = getBackgroundColor(scheme);
    DiffDrawUtil.drawCurveTrapezium(g, x1, x2, start1, end1, start2, end2, color, null);
  }

  /*
   * Gutter
   */
  @Override
  public void paint(Editor editor, Graphics g, Rectangle r) {
    if (!myCondition.get()) return;

    int y = r.y;
    int lineHeight = myEditor.getLineHeight();

    EditorGutterComponentEx gutter = ((EditorEx)editor).getGutterComponentEx();
    int annotationsOffset = gutter.getAnnotationsAreaOffset();
    int annotationsWidth = gutter.getAnnotationsAreaWidth();
    if (annotationsWidth != 0) {
      g.setColor(editor.getColorsScheme().getColor(EditorColors.GUTTER_BACKGROUND));
      g.fillRect(annotationsOffset, y, annotationsWidth, lineHeight);
    }

    draw(g, 0, y, lineHeight, myEditor.getColorsScheme());
  }

  /*
   * Editor
   */
  @Override
  public void drawLine(Graphics g, int x1, int x2, int y) {
    if (!myCondition.get()) return;

    y++; // we want y to be line's top position

    final int gutterWidth = ((EditorEx)myEditor).getGutterComponentEx().getWidth();
    int lineHeight = myEditor.getLineHeight();
    int interval = getStepSize(lineHeight) * 2;

    int shiftX = -interval; // skip zero index painting
    if (DiffUtil.isMirrored(myEditor)) {
      int contentWidth = ((EditorEx)myEditor).getScrollPane().getViewport().getWidth();
      shiftX += contentWidth % interval - interval;
      shiftX += gutterWidth % interval - interval;
    }
    else {
      shiftX += -gutterWidth % interval - interval;
    }

    draw(g, shiftX, y, lineHeight, myEditor.getColorsScheme());
  }

  @NotNull
  @Override
  public LineMarkerRendererEx.Position getPosition() {
    return LineMarkerRendererEx.Position.CUSTOM;
  }

  private static void draw(@NotNull Graphics g,
                           int shiftX,
                           int shiftY,
                           int lineHeight,
                           @Nullable EditorColorsScheme scheme) {
    int step = getStepSize(lineHeight);
    int height = getHeight(lineHeight);

    Rectangle clip = g.getClipBounds();
    if (clip.width <= 0) return;
    int count = (clip.width / step + 3);
    int shift = (clip.x - shiftX) / step;

    int[] xPoints = new int[count];
    int[] yPoints = new int[count];

    shiftY += (lineHeight - height - step) / 2;

    for (int index = 0; index < count; index++) {
      int absIndex = index + shift;

      int xPos = absIndex * step + shiftX;
      int yPos;

      if (absIndex == 0) {
        yPos = step / 2 + shiftY;
      }
      else if (absIndex % 2 == 0) {
        yPos = shiftY;
      }
      else {
        yPos = step + shiftY;
      }

      xPoints[index] = xPos;
      yPoints[index] = yPos;
    }

    GraphicsConfig config = GraphicsUtil.disableAAPainting(g);
    try {
      paintLine(g, xPoints, yPoints, lineHeight, scheme);
    }
    finally {
      config.restore();
    }
  }

  private static void paintLine(@NotNull Graphics g,
                                @NotNull int[] xPoints, @NotNull int[] yPoints,
                                int lineHeight,
                                @Nullable EditorColorsScheme scheme) {
    int height = getHeight(lineHeight);
    if (scheme == null) scheme = EditorColorsManager.getInstance().getGlobalScheme();

    g.setColor(getBackgroundColor(scheme));

    Graphics2D gg = ((Graphics2D)g);
    AffineTransform oldTransform = gg.getTransform();

    for (int i = 0; i < height; i++) {
      gg.drawPolyline(xPoints, yPoints, xPoints.length);
      gg.translate(0, 1);
    }
    gg.setTransform(oldTransform);
  }

  //
  // Parameters
  //

  public static final ColorKey BACKGROUND = ColorKey.createColorKey("DIFF_SEPARATORS_BACKGROUND");

  private static int getStepSize(int lineHeight) {
    return Math.max(lineHeight / 3, 1);
  }

  private static int getHeight(int lineHeight) {
    return Math.max(lineHeight / 2, 1);
  }

  @NotNull
  private static Color getBackgroundColor(@NotNull EditorColorsScheme scheme) {
    Color color = scheme.getColor(BACKGROUND);
    return color != null ? color : Gray._128;
  }
}
