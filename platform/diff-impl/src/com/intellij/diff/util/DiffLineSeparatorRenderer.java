// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.util;

import com.intellij.codeInsight.daemon.impl.HintRenderer;
import com.intellij.openapi.diff.DiffBundle;
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
import com.intellij.openapi.editor.markup.ActiveGutterRenderer;
import com.intellij.openapi.editor.markup.LineMarkerRendererEx;
import com.intellij.openapi.editor.markup.LineSeparatorRenderer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.Gray;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.ImageUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;

@ApiStatus.Internal
public class DiffLineSeparatorRenderer implements LineMarkerRendererEx, LineSeparatorRenderer, ActiveGutterRenderer {
  private static Object[] ourCachedImageKey = null;
  private static BufferedImage outCachedImage = null;

  @NotNull private final Editor myEditor;
  @NotNull private final SeparatorPresentation myPresentation;

  public DiffLineSeparatorRenderer(@NotNull Editor editor, @NotNull SeparatorPresentation presentation) {
    myEditor = editor;
    myPresentation = presentation;
  }

  /*
   * Divider
   */
  public static void drawConnectorLine(@NotNull Graphics2D g,
                                       int x1, int x2,
                                       int y1, int y2,
                                       int lineHeight,
                                       boolean isHovered,
                                       @Nullable EditorColorsScheme scheme) {
    if (x1 == x2) return;

    int step = getStepSize(lineHeight);
    int height = getHeight(lineHeight);
    int extraGap = getAAGap();
    int verticalOffset = getVerticalOffset(lineHeight);

    int verticalAlign = verticalOffset + height + extraGap;
    y1 += verticalAlign;
    y2 += verticalAlign;

    Path2D path = new Path2D.Double();

    // 0.5f: fix rounding issues in mirrored mode, with scale(*-1*, 1)
    path.moveTo(x1 - 0.5f, y1);

    double delta = (double)Math.abs(y2 - y1) / Math.abs(x2 - x1);
    if (delta < 0.2) {
      double middleX = (double)(x1 + x2) / 2;
      double middleY = (double)(y1 + y2) / 2;
      if (x2 - x1 > 5 * step) {
        path.quadTo(x1 + step * 0.5, y1 + height,
                    x1 + step, y1 + height);
        path.quadTo(x1 + step * 1.5, y1 + height,
                    x1 + step * 2.0, middleY);
        path.quadTo(x1 + step * 2.5, middleY - height,
                    middleX, middleY - height);
        path.quadTo(x2 - step * 2.5, middleY - height,
                    x2 - step * 2.0, middleY);
        path.quadTo(x2 - step * 1.5, y2 + height,
                    x2 - step * 1.0, y2 + height);
        path.quadTo(x2 - step * 0.5, y2 + height,
                    x2, y2);
      }
      else {
        // fallback: divider has the wrong size. Can't fit 6 half-periods nicely - use 2 half-periods instead.
        path.quadTo(middleX, middleY + 2 * height,
                    x2, y2);
      }
    }
    else if (y1 > y2) {
      path.curveTo(x1 + step * 0.125, y1 + height * 0.125,
                   x1 + step * 0.125, y1 + height * 0.5,
                   x1 + step * 0.5, y1 + height * 0.5);
      path.curveTo(x2 - step * 2.0, y1 + height * 0.5,
                   x2 - step * 2.0, y2 + 2 * height * 2.0,
                   x2, y2);
    }
    else {
      path.curveTo(x1 + step * 2.0, y1 + 2 * height * 2.0,
                   x1 + step * 2.0, y2 + height * 0.5,
                   x2 - step * 0.5, y2 + height * 0.5);
      path.curveTo(x2 - step * 0.125, y2 + height * 0.5,
                   x2 - step * 0.125, y2 + height * 0.125,
                   x2, y2);
    }

    g.setColor(getWaveColor(scheme));
    g.setStroke(getStroke(isHovered));
    g.draw(path);
  }

  /*
   * Gutter
   */
  @Override
  public void paint(@NotNull Editor editor, @NotNull Graphics g, @NotNull Rectangle r) {
    if (!myPresentation.isVisible()) return;
    boolean isHovered = myPresentation.isHovered();

    int y = r.y;
    int lineHeight = myEditor.getLineHeight();

    EditorGutterComponentEx gutter = ((EditorEx)editor).getGutterComponentEx();
    int annotationsOffset = gutter.getAnnotationsAreaOffset();
    int annotationsWidth = gutter.getAnnotationsAreaWidth();
    if (annotationsWidth != 0) {
      g.setColor(editor.getColorsScheme().getColor(EditorColors.GUTTER_BACKGROUND));
      g.fillRect(annotationsOffset, y, annotationsWidth, lineHeight);
    }

    boolean isMirrored = DiffUtil.isMirrored(myEditor);
    int shiftX = getStartPhase(lineHeight, isMirrored);
    draw(g, shiftX, y, lineHeight, isHovered, myEditor.getColorsScheme());
  }

  @Override
  public boolean canDoAction(@NotNull Editor editor, @NotNull MouseEvent e) {
    return myPresentation.isVisible() && myPresentation.isHovered();
  }

  @Override
  public void doAction(@NotNull Editor editor, @NotNull MouseEvent e) {
    myPresentation.setExpanded(true);
  }

  @Override
  public @NotNull String getAccessibleName() {
    return DiffBundle.message("diff.unchanged.lines.folding.marker.renderer");
  }

  /*
   * Editor
   */
  @Override
  public void drawLine(Graphics g, int x1, int x2, int y) {
    if (!myPresentation.isVisible()) return;
    boolean isHovered = myPresentation.isHovered();

    y++; // we want y to be line's top position

    final int gutterWidth = ((EditorEx)myEditor).getGutterComponentEx().getWidth();
    int lineHeight = myEditor.getLineHeight();
    int interval = getStepSize(lineHeight) * 4;

    JScrollPane pane = ((EditorEx)myEditor).getScrollPane();
    boolean isMirrored = DiffUtil.isMirrored(myEditor);

    int shiftX = -interval; // skip zero index painting
    if (isMirrored) {
      int contentWidth = pane.getViewport().getWidth();
      shiftX += contentWidth % interval - interval;
      shiftX += gutterWidth % interval - interval;
      shiftX -= getStartPhase(lineHeight, isMirrored);
    }
    else {
      shiftX += -gutterWidth % interval - interval;
      shiftX += getStartPhase(lineHeight, isMirrored);
    }
    shiftX += pane.getHorizontalScrollBar().getValue(); // do not move wave with scrolling

    String description = myEditor instanceof EditorImpl ? myPresentation.getDescription() : null;
    if (description != null) {
      drawWithDescription((Graphics2D)g, x1, y, shiftX, lineHeight, isHovered, (EditorImpl)myEditor, description);
    }
    else {
      draw(g, shiftX, y, lineHeight, isHovered, myEditor.getColorsScheme());
    }
  }

  @SuppressWarnings("GraphicsSetClipInspection")
  private static void drawWithDescription(Graphics2D g,
                                          int x,
                                          int y,
                                          int shiftX,
                                          int lineHeight,
                                          boolean isHovered,
                                          @NotNull EditorImpl editor,
                                          @NotNull String description) {
    EditorColorsScheme scheme = editor.getColorsScheme();
    int rectX = x + JBUIScale.scale(5);
    int rectWidth = HintRenderer.calcWidthInPixels(editor, description, null);

    Shape oldClip = g.getClip();
    g.clip(new Rectangle(0, 0, rectX, Integer.MAX_VALUE));
    draw(g, shiftX, y, lineHeight, isHovered, editor.getColorsScheme());
    g.setClip(oldClip);

    g.clip(new Rectangle(rectX + rectWidth, 0, Integer.MAX_VALUE, Integer.MAX_VALUE));
    draw(g, shiftX, y, lineHeight, isHovered, editor.getColorsScheme());
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
                           boolean isHovered,
                           @NotNull EditorColorsScheme scheme) {
    int step = getStepSize(lineHeight);
    int height = getHeight(lineHeight);
    Color color = getWaveColor(scheme);

    Rectangle clip = g.getClipBounds();
    if (clip.width <= 0) return;

    int startX = clip.x - shiftX;
    int endX = startX + clip.width;

    int startIndex = startX / step - 4;
    int endIndex = endX / step + 1;

    Graphics2D gg = (Graphics2D)g.create();
    gg.translate(shiftX, shiftY + getVerticalOffset(lineHeight));

    BufferedImage image = createImage(gg, color, isHovered, step, height);
    gg.setComposite(AlphaComposite.SrcOver);

    for (int index = startIndex; index < endIndex; index++) {
      if (index % 4 == 0) {
        UIUtil.drawImage(gg, image, index * step, 0, null);
      }
    }
    gg.dispose();
  }

  @SuppressWarnings("UnnecessaryLocalVariable")
  @NotNull
  private static BufferedImage createImage(@NotNull Graphics2D g, @NotNull Color color, boolean isHovered, int step, int height) {
    Object[] key = new Object[]{color.getRGB(), JBUIScale.sysScale(g), isHovered, step, height};
    if (Arrays.equals(ourCachedImageKey, key) && outCachedImage != null) return outCachedImage;

    int extraGap = getAAGap();
    int imageWidth = step * 4;
    int imageHeight = height * 2 + extraGap * 2;
    BufferedImage image = ImageUtil.createImage(g, imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB);

    Graphics2D gg = image.createGraphics();

    double upper = extraGap;
    double center = height + extraGap;
    double lower = height * 2 + extraGap;
    double dx = (double)step / 2;

    Path2D path = new Path2D.Double();
    path.moveTo(0, upper);
    path.quadTo(step * 1.0 - dx, upper,
                step * 1.0, center);
    path.quadTo(step * 2.0 - dx, lower,
                step * 2.0, lower);
    path.quadTo(step * 3.0 - dx, lower,
                step * 3.0, center);
    path.quadTo(step * 4.0 - dx, upper,
                step * 4.0, upper);

    GraphicsUtil.setupAAPainting(gg);
    gg.setStroke(getStroke(isHovered));
    gg.setColor(color);
    gg.draw(path);

    outCachedImage = image;
    ourCachedImageKey = key;

    return image;
  }

  //
  // Parameters
  //

  /**
   * @deprecated Backward compatibility with old color schemes
   */
  @Deprecated
  public static final ColorKey BACKGROUND = ColorKey.createColorKey("DIFF_SEPARATORS_BACKGROUND");
  public static final ColorKey FOREGROUND = ColorKey.createColorKey("DIFF_SEPARATOR_WAVE");

  /**
   * @return quarter-period of the wave pattern, 4 steps per wave
   */
  private static int getStepSize(int lineHeight) {
    // the divider needs ~3 half-periods (6 steps) to be drawn nicely
    return Math.max(JBUIScale.scale(Registry.intValue("diff.divider.width")) / 6, 2);
  }

  /**
   * @return half-height of the wave pattern
   */
  private static int getHeight(int lineHeight) {
    return JBUI.scale(3);
  }

  private static int getVerticalOffset(int lineHeight) {
    int height = getHeight(lineHeight);
    return (lineHeight - 2 * height - 2 * getAAGap()) / 2;
  }

  private static int getStartPhase(int lineHeight, boolean isMirror) {
    return getStepSize(lineHeight); // cross divider at a 45' downwards angle, to allow a smaller 'shoulder'
  }

  private static int getAAGap() {
    return 1; // gap for proper AA rendering
  }

  @NotNull
  private static Color getWaveColor(@Nullable EditorColorsScheme scheme) {
    if (scheme == null) scheme = EditorColorsManager.getInstance().getGlobalScheme();
    Color color = scheme.getColor(FOREGROUND);
    if (color != null) return color;
    color = scheme.getColor(BACKGROUND);
    if (color != null) return color;
    return Gray._128;
  }

  private static Stroke getStroke(boolean isHovered) {
    if (isHovered) {
      return new BasicStroke(2.0f);
    }
    else {
      return new BasicStroke(1.0f);
    }
  }

  public interface SeparatorPresentation {
    boolean isVisible();

    boolean isHovered();

    @Nullable
    String getDescription();

    void setExpanded(boolean value);
  }
}
