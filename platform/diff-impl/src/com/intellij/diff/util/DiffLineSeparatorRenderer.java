// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.util;

import com.intellij.codeInsight.daemon.impl.HintRenderer;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.markup.LineMarkerRendererEx;
import com.intellij.openapi.editor.markup.LineSeparatorRenderer;
import com.intellij.openapi.util.BooleanGetter;
import com.intellij.ui.Gray;
import com.intellij.ui.paint.LinePainter2D;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Arrays;

public class DiffLineSeparatorRenderer implements LineMarkerRendererEx, LineSeparatorRenderer {
  private static Object[] ourCachedImageKey = null;
  private static BufferedImage outCachedImage = null;

  @NotNull private final Editor myEditor;
  @NotNull private final BooleanGetter myCondition;
  @Nullable private final String myDescription;

  public DiffLineSeparatorRenderer(@NotNull Editor editor, @NotNull BooleanGetter condition, @Nullable String description) {
    myEditor = editor;
    myCondition = condition;
    myDescription = description;
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
    int verticalOffset = getVerticalOffset(lineHeight, step, height);

    int start1 = y1 + verticalOffset + step / 2;
    int start2 = y2 + verticalOffset + step / 2;
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

    if (myDescription != null && myEditor instanceof EditorImpl) {
      drawWithDescription((Graphics2D)g, x1, y, shiftX, lineHeight, (EditorImpl)myEditor, myDescription);
    }
    else {
      draw(g, shiftX, y, lineHeight, myEditor.getColorsScheme());
    }
  }

  private static void drawWithDescription(Graphics2D g,
                                          int x,
                                          int y,
                                          int shiftX,
                                          int lineHeight,
                                          @NotNull EditorImpl editor,
                                          @NotNull String description) {
    EditorColorsScheme scheme = editor.getColorsScheme();
    int rectX = x + JBUIScale.scale(5);
    int rectWidth = HintRenderer.calcWidthInPixels(editor, description, null);

    Shape oldClip = g.getClip();
    g.clip(new Rectangle(0, 0, rectX, Integer.MAX_VALUE));
    draw(g, shiftX, y, lineHeight, editor.getColorsScheme());
    g.setClip(oldClip);

    g.clip(new Rectangle(rectX + rectWidth, 0, Integer.MAX_VALUE, Integer.MAX_VALUE));
    draw(g, shiftX, y, lineHeight, editor.getColorsScheme());
    g.setClip(oldClip);

    HintRenderer.paintHint(g, editor,
                           new Rectangle(rectX, y, rectWidth, lineHeight),
                           description,
                           scheme.getAttributes(DefaultLanguageHighlighterColors.INLINE_PARAMETER_HINT),
                           scheme.getAttributes(HighlighterColors.TEXT), null);
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
                           @NotNull EditorColorsScheme scheme) {
    int step = getStepSize(lineHeight);
    int height = getHeight(lineHeight);
    Color color = getBackgroundColor(scheme);

    Rectangle clip = g.getClipBounds();
    if (clip.width <= 0) return;

    int startX = clip.x - shiftX;
    int endX = startX + clip.width;

    int startIndex = startX / step;
    int endIndex = endX / step + 1;

    Graphics2D gg = (Graphics2D)g.create();
    gg.translate(shiftX, shiftY + getVerticalOffset(lineHeight, step, height));

    if (startIndex == 0) {
      gg.setColor(color);
      LinePainter2D.fillPolygon(gg,
                                new double[]{0, step, step, 0},
                                new double[]{step / 2, step, step + height, step / 2 + height}, 4,
                                LinePainter2D.StrokeType.CENTERED_CAPS_SQUARE, 1.0,
                                RenderingHints.VALUE_ANTIALIAS_OFF);
    }
    else if (startIndex % 2 == 0) {
      startIndex--; // we should start painting with even index
    }

    BufferedImage image = createImage(gg, color, step, height);
    gg.setComposite(AlphaComposite.SrcOver);

    for (int index = startIndex; index < endIndex; index++) {
      if (index % 2 == 0) continue;
      UIUtil.drawImage(gg, image, index * step, 0, null);
    }
    gg.dispose();
  }

  @NotNull
  private static BufferedImage createImage(@NotNull Graphics2D g, @NotNull Color color, int step, int height) {
    Object[] key = new Object[]{color.getRGB(), JBUIScale.sysScale(g), step, height};
    if (Arrays.equals(ourCachedImageKey, key) && outCachedImage != null) return outCachedImage;

    int imageWidth = step * 2;
    int imageHeight = step + height;
    BufferedImage image = UIUtil.createImage(g, imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB);

    Graphics2D gg = image.createGraphics();

    gg.setColor(color);
    LinePainter2D.fillPolygon(gg,
                              new double[]{0, step, step * 2, step * 2, step, 0},
                              new double[]{step, 0, step, step + height, height, step + height}, 6,
                              LinePainter2D.StrokeType.CENTERED_CAPS_SQUARE, 1.0,
                              RenderingHints.VALUE_ANTIALIAS_OFF);

    outCachedImage = image;
    ourCachedImageKey = key;

    return image;
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

  private static int getVerticalOffset(int lineHeight, int step, int height) {
    return (lineHeight - height - step) / 2;
  }

  @NotNull
  private static Color getBackgroundColor(@Nullable EditorColorsScheme scheme) {
    if (scheme == null) scheme = EditorColorsManager.getInstance().getGlobalScheme();
    Color color = scheme.getColor(BACKGROUND);
    return color != null ? color : Gray._128;
  }
}
