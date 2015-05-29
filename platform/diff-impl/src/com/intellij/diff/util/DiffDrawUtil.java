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
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.util.BooleanGetter;
import com.intellij.ui.JBColor;
import com.intellij.util.DocumentUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.geom.CubicCurve2D;
import java.awt.geom.Path2D;

public class DiffDrawUtil {
  private DiffDrawUtil() {
  }

  @NotNull
  public static Color getDividerColor() {
    return getDividerColor(null);
  }

  @NotNull
  public static Color getDividerColor(@Nullable Editor editor) {
    return getDividerColorFromScheme(editor != null ? editor.getColorsScheme() : EditorColorsManager.getInstance().getGlobalScheme());
  }

  @NotNull
  public static Color getDividerColorFromScheme(@NotNull EditorColorsScheme scheme) {
    Color gutterBackground = scheme.getColor(EditorColors.GUTTER_BACKGROUND);
    if (gutterBackground == null) {
      gutterBackground = EditorColors.GUTTER_BACKGROUND.getDefaultColor();
    }
    return gutterBackground;
  }

  public static void drawConnectorLineSeparator(@NotNull Graphics2D g,
                                                int x1, int x2,
                                                int start1, int end1,
                                                int start2, int end2) {
    drawConnectorLineSeparator(g, x1, x2, start1, end1, start2, end2, null);
  }

  public static void drawConnectorLineSeparator(@NotNull Graphics2D g,
                                                int x1, int x2,
                                                int start1, int end1,
                                                int start2, int end2,
                                                @Nullable EditorColorsScheme scheme) {
    DiffLineSeparatorRenderer.drawConnectorLine(g, x1, x2, start1, start2, end1 - start1, scheme);
  }

  public static void drawDoubleChunkBorderLine(@NotNull Graphics2D g, int x1, int x2, int y, @NotNull Color color) {
    UIUtil.drawLine(g, x1, y, x2, y, null, color);
    UIUtil.drawLine(g, x1, y + 1, x2, y + 1, null, color);
  }

  public static void drawChunkBorderLine(@NotNull Graphics2D g, int x1, int x2, int y, @NotNull Color color) {
    UIUtil.drawLine(g, x1, y, x2, y, null, color);
  }

  public static void drawDottedDoubleChunkBorderLine(@NotNull Graphics2D g, int x1, int x2, int y, @NotNull Color color) {
    UIUtil.drawBoldDottedLine(g, x1, x2, y - 1, null, color, false);
    UIUtil.drawBoldDottedLine(g, x1, x2, y, null, color, false);
  }

  public static void drawDottedChunkBorderLine(@NotNull Graphics2D g, int x1, int x2, int y, @NotNull Color color) {
    UIUtil.drawBoldDottedLine(g, x1, x2, y - 1, null, color, false);
  }

  public static void drawChunkBorderLine(@NotNull Graphics2D g, int x1, int x2, int y, @NotNull Color color,
                                         boolean doubleLine, boolean dottedLine) {
    if (dottedLine && doubleLine) {
      drawDottedDoubleChunkBorderLine(g, x1, x2, y, color);
    } else if (dottedLine) {
      drawDottedChunkBorderLine(g, x1, x2, y, color);
    } else if (doubleLine) {
      drawDoubleChunkBorderLine(g, x1, x2, y, color);
    } else {
      drawChunkBorderLine(g, x1, x2, y, color);
    }
  }

  public static void drawTrapezium(@NotNull Graphics2D g,
                                   int x1, int x2,
                                   int start1, int end1,
                                   int start2, int end2,
                                   @NotNull Color color) {
    drawTrapezium(g, x1, x2, start1, end1, start2, end2, color, null);
  }

  public static void drawTrapezium(@NotNull Graphics2D g,
                                   int x1, int x2,
                                   int start1, int end1,
                                   int start2, int end2,
                                   @Nullable Color fillColor,
                                   @Nullable Color borderColor) {
    if (fillColor != null) {
      final int[] xPoints = new int[]{x1, x2, x2, x1};
      final int[] yPoints = new int[]{start1, start2, end2 + 1, end1 + 1};

      g.setColor(fillColor);
      g.fillPolygon(xPoints, yPoints, xPoints.length);
    }

    if (borderColor != null) {
      g.setColor(borderColor);
      g.drawLine(x1, start1, x2, start2);
      g.drawLine(x1, end1, x2, end2);
    }
  }

  public static void drawCurveTrapezium(@NotNull Graphics2D g,
                                        int x1, int x2,
                                        int start1, int end1,
                                        int start2, int end2,
                                        @NotNull Color color) {
    drawCurveTrapezium(g, x1, x2, start1, end1, start2, end2, color, null);
  }

  public static void drawCurveTrapezium(@NotNull Graphics2D g,
                                        int x1, int x2,
                                        int start1, int end1,
                                        int start2, int end2,
                                        @Nullable Color fillColor,
                                        @Nullable Color borderColor) {
    Shape upperCurve = makeCurve(x1, x2, start1, start2, true);
    Shape lowerCurve = makeCurve(x1, x2, end1 + 1, end2 + 1, false);
    Shape lowerCurveBorder = makeCurve(x1, x2, end1, end2, false);

    if (fillColor != null) {
      Path2D path = new Path2D.Double();
      path.append(upperCurve, true);
      path.append(lowerCurve, true);

      g.setColor(fillColor);
      g.fill(path);
    }

    if (borderColor != null) {
      g.setColor(borderColor);
      g.draw(upperCurve);
      g.draw(lowerCurveBorder);
    }
  }

  public static final double CTRL_PROXIMITY_X = 0.3;

  private static Shape makeCurve(int x1, int x2, int y1, int y2, boolean forward) {
    int width = x2 - x1;
    if (forward) {
      return new CubicCurve2D.Double(x1, y1,
                                     x1 + width * CTRL_PROXIMITY_X, y1,
                                     x1 + width * (1.0 - CTRL_PROXIMITY_X), y2,
                                     x1 + width, y2);
    }
    else {
      return new CubicCurve2D.Double(x1 + width, y2,
                                     x1 + width * (1.0 - CTRL_PROXIMITY_X), y2,
                                     x1 + width * CTRL_PROXIMITY_X, y1,
                                     x1, y1);
    }
  }

  //
  // Colors
  //

  @NotNull
  public static TextAttributes getTextAttributes(@NotNull final TextDiffType type,
                                                 @Nullable final Editor editor,
                                                 final boolean ignored,
                                                 final boolean showStripes) {
    return new TextAttributes() {
      @Override
      public Color getBackgroundColor() {
        return ignored ? type.getIgnoredColor(editor) : type.getColor(editor);
      }

      @Override
      public Color getErrorStripeColor() {
        return showStripes ? type.getMarkerColor(editor) : null;
      }
    };
  }

  @NotNull
  public static TextAttributes getStripeTextAttributes(@NotNull final TextDiffType type,
                                                       @NotNull final Editor editor) {
    return new TextAttributes() {
      @Override
      public Color getErrorStripeColor() {
        return type.getMarkerColor(editor);
      }
    };
  }

  //
  // Highlighters
  //

  // TODO: invalid highlighting of empty last line
  // TODO: invalid highlighting in case on deletion '\n' before range
  // TODO: desync of range and line markers

  @NotNull
  public static RangeHighlighter createHighlighter(@NotNull Editor editor, int start, int end, @NotNull TextDiffType type) {
    return createHighlighter(editor, start, end, type, false);
  }

  @NotNull
  public static RangeHighlighter createHighlighter(@NotNull Editor editor, int start, int end, @NotNull TextDiffType type,
                                                   boolean ignored) {
    return createHighlighter(editor, start, end, type, ignored, HighlighterTargetArea.EXACT_RANGE);
  }

  @NotNull
  public static RangeHighlighter createHighlighter(@NotNull Editor editor, int start, int end, @NotNull TextDiffType type,
                                                   boolean ignored, @NotNull HighlighterTargetArea area) {
    return createHighlighter(editor, start, end, type, ignored, area, false);
  }

  @NotNull
  public static RangeHighlighter createHighlighter(@NotNull Editor editor, int start, int end, @NotNull TextDiffType type,
                                                   boolean ignored, @NotNull HighlighterTargetArea area, boolean resolved) {
    TextAttributes attributes = resolved ? null : getTextAttributes(type, editor, ignored, true);

    RangeHighlighter highlighter = editor.getMarkupModel().addRangeHighlighter(start, end, HighlighterLayer.SELECTION - 3,
                                                                               start != end ? attributes : null, area);

    // TODO: diff looks cool with wide markers. Maybe we can keep them ?
    highlighter.setThinErrorStripeMark(true);

    installGutterRenderer(highlighter, type, ignored, resolved);

    return highlighter;
  }

  @NotNull
  public static RangeHighlighter createInlineHighlighter(@NotNull Editor editor, int start, int end, @NotNull TextDiffType type) {
    TextAttributes attributes = getTextAttributes(type, editor, false, false);

    RangeHighlighter highlighter = editor.getMarkupModel()
      .addRangeHighlighter(start, end, HighlighterLayer.SELECTION - 2, attributes, HighlighterTargetArea.EXACT_RANGE);

    if (start == end) installEmptyRangeRenderer(highlighter, type);

    return highlighter;
  }

  public static void installGutterRenderer(@NotNull RangeHighlighter highlighter,
                                           @NotNull TextDiffType type) {
    installGutterRenderer(highlighter, type, false);
  }

  public static void installGutterRenderer(@NotNull RangeHighlighter highlighter,
                                           @NotNull TextDiffType type,
                                           boolean ignoredFoldingOutline) {
    installGutterRenderer(highlighter, type, ignoredFoldingOutline, false);
  }

  public static void installGutterRenderer(@NotNull RangeHighlighter highlighter,
                                           @NotNull TextDiffType type,
                                           boolean ignoredFoldingOutline,
                                           boolean resolved) {
    highlighter.setLineMarkerRenderer(new DiffLineMarkerRenderer(type, ignoredFoldingOutline, resolved));
  }

  public static void installEmptyRangeRenderer(@NotNull RangeHighlighter highlighter, @NotNull TextDiffType type) {
    highlighter.setCustomRenderer(new DiffEmptyHighlighterRenderer(type));
  }

  @NotNull
  public static RangeHighlighter createLineMarker(@NotNull Editor editor, int line, @NotNull final TextDiffType type,
                                                  @NotNull final SeparatorPlacement placement) {
    return createLineMarker(editor, line, type, placement, false);
  }

  @NotNull
  public static RangeHighlighter createLineMarker(@NotNull final Editor editor, int line, @NotNull final TextDiffType type,
                                                  @NotNull final SeparatorPlacement placement, final boolean doubleLine) {
    return createLineMarker(editor, line, type, placement, doubleLine, false);
  }

  @NotNull
  public static RangeHighlighter createLineMarker(@NotNull final Editor editor, int line, @NotNull final TextDiffType type,
                                                  @NotNull final SeparatorPlacement placement, final boolean doubleLine,
                                                  final boolean applied) {
    TextAttributes attributes = applied ? null : getStripeTextAttributes(type, editor);
    LineSeparatorRenderer renderer = new LineSeparatorRenderer() {
      @Override
      public void drawLine(Graphics g, int x1, int x2, int y) {
        // TODO: change LineSeparatorRenderer interface ?
        Rectangle clip = g.getClipBounds();
        x2 = clip.x + clip.width;
        drawChunkBorderLine((Graphics2D)g, x1, x2, y, type.getColor(editor), doubleLine, applied);
      }
    };
    return createLineMarker(editor, line, placement, attributes, renderer);
  }

  @NotNull
  public static RangeHighlighter createBorderLineMarker(@NotNull final Editor editor, int line,
                                                        @NotNull final SeparatorPlacement placement) {
    LineSeparatorRenderer renderer = new LineSeparatorRenderer() {
      @Override
      public void drawLine(Graphics g, int x1, int x2, int y) {
        // TODO: change LineSeparatorRenderer interface ?
        Rectangle clip = g.getClipBounds();
        x2 = clip.x + clip.width;
        g.setColor(JBColor.border());
        g.drawLine(x1, y, x2, y);
      }
    };
    return createLineMarker(editor, line, placement, null, renderer);
  }

  @NotNull
  public static RangeHighlighter createLineMarker(@NotNull final Editor editor, int line, @NotNull final SeparatorPlacement placement,
                                                  @Nullable TextAttributes attributes, @NotNull LineSeparatorRenderer renderer) {
    int offset = DocumentUtil.getFirstNonSpaceCharOffset(editor.getDocument(), line);
    RangeHighlighter marker = editor.getMarkupModel().addRangeHighlighter(offset, offset, HighlighterLayer.SELECTION - 1, attributes,
                                                                          HighlighterTargetArea.LINES_IN_RANGE);
    marker.setThinErrorStripeMark(true);

    // We won't use addLineHighlighter as it will fail to add marker into empty document.
    //RangeHighlighter marker = editor.getMarkupModel().addLineHighlighter(line, HighlighterLayer.SELECTION - 1, null);

    marker.setLineSeparatorPlacement(placement);
    marker.setLineSeparatorRenderer(renderer);

    return marker;
  }

  @NotNull
  public static RangeHighlighter createLineSeparatorHighlighter(@NotNull Editor editor,
                                                                int offset1,
                                                                int offset2,
                                                                @NotNull BooleanGetter condition) {
    RangeHighlighter marker = editor.getMarkupModel().addRangeHighlighter(offset1, offset2, HighlighterLayer.SELECTION - 1, null,
                                                                          HighlighterTargetArea.LINES_IN_RANGE);

    DiffLineSeparatorRenderer renderer = new DiffLineSeparatorRenderer(editor, condition);
    marker.setLineSeparatorPlacement(SeparatorPlacement.TOP);
    marker.setLineSeparatorRenderer(renderer);
    marker.setLineMarkerRenderer(renderer);

    return marker;
  }
}
