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
package com.intellij.openapi.vcs.ex;

import com.intellij.diff.util.DiffDrawUtil;
import com.intellij.openapi.diff.DiffColors;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.Function;
import com.intellij.util.PairConsumer;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.List;

import static com.intellij.diff.util.DiffDrawUtil.lineToY;

public abstract class LineStatusMarkerRenderer implements ActiveGutterRenderer {

  @NotNull protected final Range myRange;

  public LineStatusMarkerRenderer(@NotNull Range range) {
    myRange = range;
  }

  @NotNull
  public static RangeHighlighter createRangeHighlighter(@NotNull Range range,
                                                        @NotNull TextRange textRange,
                                                        @NotNull MarkupModel markupModel) {
    TextAttributes attributes = getTextAttributes(range);

    final RangeHighlighter highlighter = markupModel.addRangeHighlighter(textRange.getStartOffset(), textRange.getEndOffset(),
                                                                         DiffDrawUtil.LST_LINE_MARKER_LAYER, attributes,
                                                                         HighlighterTargetArea.LINES_IN_RANGE);

    highlighter.setThinErrorStripeMark(true);
    highlighter.setGreedyToLeft(true);
    highlighter.setGreedyToRight(true);

    return highlighter;
  }

  @NotNull
  public static LineMarkerRenderer createRenderer(@NotNull Range range,
                                                  @Nullable Function<Editor, LineStatusMarkerPopup> popupBuilder) {
    return new LineStatusMarkerRenderer(range) {
      @Override
      public boolean canDoAction(MouseEvent e) {
        return popupBuilder != null && isInsideMarkerArea(e);
      }

      @Override
      public void doAction(Editor editor, MouseEvent e) {
        LineStatusMarkerPopup popup = popupBuilder != null ? popupBuilder.fun(editor) : null;
        if (popup != null) popup.showHint(e);
      }
    };
  }

  @NotNull
  public static LineMarkerRenderer createRenderer(int line1, int line2, @NotNull Color color, @Nullable String tooltip,
                                                  @Nullable PairConsumer<Editor, MouseEvent> action) {
    return new ActiveGutterRenderer() {
      @Override
      public void paint(Editor editor, Graphics g, Rectangle r) {
        Rectangle area = getMarkerArea(editor, r, line1, line2);
        Color borderColor = getGutterBorderColor(editor);
        if (area.height != 0) {
          paintRect(g, color, borderColor, area.x, area.y, area.x + area.width, area.y + area.height);
        }
        else {
          paintTriangle(g, color, borderColor, area.x, area.x + area.width, area.y);
        }
      }

      @Nullable
      @Override
      public String getTooltipText() {
        return tooltip;
      }

      @Override
      public boolean canDoAction(MouseEvent e) {
        return isInsideMarkerArea(e);
      }

      @Override
      public void doAction(Editor editor, MouseEvent e) {
        if (action != null) action.consume(editor, e);
      }
    };
  }

  @NotNull
  private static TextAttributes getTextAttributes(@NotNull final Range range) {
    return new TextAttributes() {
      @Override
      public Color getErrorStripeColor() {
        return LineStatusMarkerRenderer.getErrorStripeColor(range, null);
      }
    };
  }

  //
  // Gutter painting
  //

  protected int getFramingBorderSize() {
    return 0;
  }

  @Override
  public void paint(Editor editor, Graphics g, Rectangle r) {
    Color gutterColor = getGutterColor(myRange, editor);
    Color borderColor = getGutterBorderColor(editor);
    Color gutterBackgroundColor = ((EditorEx)editor).getGutterComponentEx().getBackground();

    Rectangle area = getMarkerArea(editor, r, myRange.getLine1(), myRange.getLine2());
    final int x = area.x;
    final int endX = area.x + area.width;
    final int y = area.y;
    final int endY = area.y + area.height;

    int framingBorder = getFramingBorderSize();
    if (framingBorder > 0) {
      if (y != endY) {
        g.setColor(gutterBackgroundColor);
        g.fillRect(x - framingBorder, y - framingBorder,
                   endX - x + framingBorder, endY - y + framingBorder * 2);
      }
    }

    if (y == endY) {
      paintTriangle(g, gutterColor, borderColor, x, endX, y);
    }
    else {
      if (myRange.getInnerRanges() == null) { // Mode.DEFAULT
        paintRect(g, gutterColor, borderColor, x, y, endX, endY);
      }
      else { // Mode.SMART
        List<Range.InnerRange> innerRanges = myRange.getInnerRanges();
        for (Range.InnerRange innerRange : innerRanges) {
          if (innerRange.getType() == Range.DELETED) continue;

          int start = lineToY(editor, innerRange.getLine1());
          int end = lineToY(editor, innerRange.getLine2());

          paintRect(g, getGutterColor(innerRange, editor), null, x, start, endX, end);
        }

        paintRect(g, null, borderColor, x, y, endX, endY);

        for (Range.InnerRange innerRange : innerRanges) {
          if (innerRange.getType() != Range.DELETED) continue;

          int start = lineToY(editor, innerRange.getLine1());

          paintTriangle(g, getGutterColor(innerRange, editor), borderColor, x, endX, start);
        }
      }
    }
  }

  private static void paintRect(@NotNull Graphics g, @Nullable Color color, @Nullable Color borderColor, int x1, int y1, int x2, int y2) {
    if (color != null) {
      g.setColor(color);
      g.fillRect(x1, y1, x2 - x1, y2 - y1);
    }
    if (borderColor != null) {
      g.setColor(borderColor);
      UIUtil.drawLine(g, x1, y1, x2 - 1, y1);
      UIUtil.drawLine(g, x1, y1, x1, y2 - 1);
      UIUtil.drawLine(g, x1, y2 - 1, x2 - 1, y2 - 1);
    }
  }

  @NotNull
  public static Rectangle getMarkerArea(@NotNull Editor editor, @NotNull Rectangle r, int line1, int line2) {
    EditorGutterComponentEx gutter = ((EditorEx)editor).getGutterComponentEx();
    int x = r.x + 1; // leave 1px for brace highlighters
    int endX = gutter.getWhitespaceSeparatorOffset();
    int y = lineToY(editor, line1);
    int endY = lineToY(editor, line2);
    return new Rectangle(x, y, endX - x, endY - y);
  }

  public static boolean isInsideMarkerArea(@NotNull MouseEvent e) {
    final EditorGutterComponentEx gutter = (EditorGutterComponentEx)e.getComponent();
    return e.getX() > gutter.getLineMarkerFreePaintersAreaOffset();
  }

  private static void paintTriangle(@NotNull Graphics g, @Nullable Color color, @Nullable Color borderColor, int x1, int x2, int y) {
    int size = JBUI.scale(4);

    final int[] xPoints = new int[]{x1, x1, x2};
    final int[] yPoints = new int[]{y - size, y + size, y};

    if (color != null) {
      g.setColor(color);
      g.fillPolygon(xPoints, yPoints, xPoints.length);
    }
    if (borderColor != null) {
      g.setColor(borderColor);
      g.drawPolygon(xPoints, yPoints, xPoints.length);
    }
  }

  @Nullable
  private static Color getGutterColor(@NotNull Range.InnerRange range, @Nullable Editor editor) {
    final EditorColorsScheme scheme = getColorScheme(editor);
    switch (range.getType()) {
      case Range.INSERTED:
        return scheme.getColor(EditorColors.ADDED_LINES_COLOR);
      case Range.DELETED:
        return scheme.getColor(EditorColors.DELETED_LINES_COLOR);
      case Range.MODIFIED:
        return scheme.getColor(EditorColors.MODIFIED_LINES_COLOR);
      case Range.EQUAL:
        return scheme.getColor(EditorColors.WHITESPACES_MODIFIED_LINES_COLOR);
      default:
        assert false;
        return null;
    }
  }

  @Nullable
  private static Color getErrorStripeColor(@NotNull Range range, @Nullable Editor editor) {
    final EditorColorsScheme scheme = getColorScheme(editor);
    switch (range.getType()) {
      case Range.INSERTED:
        return scheme.getAttributes(DiffColors.DIFF_INSERTED).getErrorStripeColor();
      case Range.DELETED:
        return scheme.getAttributes(DiffColors.DIFF_DELETED).getErrorStripeColor();
      case Range.MODIFIED:
        return scheme.getAttributes(DiffColors.DIFF_MODIFIED).getErrorStripeColor();
      default:
        assert false;
        return null;
    }
  }

  @Nullable
  private static Color getGutterColor(@NotNull Range range, @Nullable Editor editor) {
    final EditorColorsScheme scheme = getColorScheme(editor);
    switch (range.getType()) {
      case Range.INSERTED:
        return scheme.getColor(EditorColors.ADDED_LINES_COLOR);
      case Range.DELETED:
        return scheme.getColor(EditorColors.DELETED_LINES_COLOR);
      case Range.MODIFIED:
        return scheme.getColor(EditorColors.MODIFIED_LINES_COLOR);
      default:
        assert false;
        return null;
    }
  }

  @Nullable
  private static Color getGutterBorderColor(@Nullable Editor editor) {
    return getColorScheme(editor).getColor(EditorColors.BORDER_LINES_COLOR);
  }

  @NotNull
  private static EditorColorsScheme getColorScheme(@Nullable Editor editor) {
    return editor != null ? editor.getColorsScheme() : EditorColorsManager.getInstance().getGlobalScheme();
  }

  //
  // Popup
  //

  @Override
  public boolean canDoAction(MouseEvent e) {
    return false;
  }

  @Override
  public void doAction(Editor editor, MouseEvent e) {
  }
}
