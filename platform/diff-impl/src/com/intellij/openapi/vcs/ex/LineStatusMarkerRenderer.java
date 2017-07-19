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
import com.intellij.diff.util.DiffUtil;
import com.intellij.openapi.diff.DiffColors;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.List;

import static com.intellij.diff.util.DiffDrawUtil.lineToY;

public abstract class LineStatusMarkerRenderer {
  @NotNull protected final LineStatusTrackerBase myTracker;

  public LineStatusMarkerRenderer(@NotNull LineStatusTrackerBase tracker) {
    myTracker = tracker;
  }


  protected boolean canDoAction(@NotNull Range range, MouseEvent e) {
    return false;
  }

  protected void doAction(@NotNull Editor editor, @NotNull Range range, MouseEvent e) {
  }

  @Nullable
  protected MarkupEditorFilter getEditorFilter() {
    return null;
  }

  protected int getFramingBorderSize() {
    return 0;
  }


  @NotNull
  RangeHighlighter createHighlighter(@NotNull Range range) {
    MarkupModel markupModel = DocumentMarkupModel.forDocument(myTracker.getDocument(), myTracker.getProject(), true);
    RangeHighlighter highlighter = createRangeHighlighter(range, markupModel);

    MarkupEditorFilter editorFilter = getEditorFilter();
    if (editorFilter != null) highlighter.setEditorFilter(editorFilter);

    highlighter.setLineMarkerRenderer(new MyActiveGutterRenderer(range));

    return highlighter;
  }


  @NotNull
  public static RangeHighlighter createRangeHighlighter(@NotNull Range range,
                                                        @NotNull MarkupModel markupModel) {
    TextRange textRange = DiffUtil.getLinesRange(markupModel.getDocument(), range.getLine1(), range.getLine2(), true);
    TextAttributes attributes = getTextAttributes(range);

    RangeHighlighter highlighter = markupModel.addRangeHighlighter(textRange.getStartOffset(), textRange.getEndOffset(),
                                                                   DiffDrawUtil.LST_LINE_MARKER_LAYER, attributes,
                                                                   HighlighterTargetArea.LINES_IN_RANGE);

    highlighter.setThinErrorStripeMark(true);
    highlighter.setGreedyToLeft(true);
    highlighter.setGreedyToRight(true);

    return highlighter;
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

  protected void paint(@NotNull Editor editor, @NotNull Range range, @NotNull Graphics g) {
    paintRange(g, editor, range, getFramingBorderSize());
  }

  public static void paintRange(@NotNull Graphics g,
                                @NotNull Editor editor,
                                @NotNull Range range,
                                int framingBorder) {
    Color gutterColor = getGutterColor(range, editor);
    Color borderColor = getGutterBorderColor(editor);
    Color gutterBackgroundColor = ((EditorEx)editor).getGutterComponentEx().getBackground();

    Rectangle area = getMarkerArea(editor, range.getLine1(), range.getLine2());
    final int x = area.x;
    final int endX = area.x + area.width;
    final int y = area.y;
    final int endY = area.y + area.height;

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
      if (range.getInnerRanges() == null) { // Mode.DEFAULT
        paintRect(g, gutterColor, borderColor, x, y, endX, endY);
      }
      else { // Mode.SMART
        List<Range.InnerRange> innerRanges = range.getInnerRanges();
        for (Range.InnerRange innerRange : innerRanges) {
          if (innerRange.getType() == Range.DELETED) continue;

          int start = lineToY(editor, range.getLine1() + innerRange.getLine1());
          int end = lineToY(editor, range.getLine1() + innerRange.getLine2());

          paintRect(g, getGutterColor(innerRange, editor), null, x, start, endX, end);
        }

        paintRect(g, null, borderColor, x, y, endX, endY);

        for (Range.InnerRange innerRange : innerRanges) {
          if (innerRange.getType() != Range.DELETED) continue;

          int start = lineToY(editor, range.getLine1() + innerRange.getLine1());

          paintTriangle(g, getGutterColor(innerRange, editor), borderColor, x, endX, start);
        }
      }
    }
  }

  public static void paintSimpleRange(Graphics g, Editor editor, int line1, int line2, @Nullable Color color) {
    Rectangle area = getMarkerArea(editor, line1, line2);
    Color borderColor = getGutterBorderColor(editor);
    if (area.height != 0) {
      paintRect(g, color, borderColor, area.x, area.y, area.x + area.width, area.y + area.height);
    }
    else {
      paintTriangle(g, color, borderColor, area.x, area.x + area.width, area.y);
    }
  }

  @NotNull
  public static Rectangle getMarkerArea(@NotNull Editor editor, int line1, int line2) {
    EditorGutterComponentEx gutter = ((EditorEx)editor).getGutterComponentEx();
    int x = gutter.getLineMarkerFreePaintersAreaOffset() + 1; // leave 1px for brace highlighters
    int endX = gutter.getWhitespaceSeparatorOffset();
    int y = lineToY(editor, line1);
    int endY = lineToY(editor, line2);
    return new Rectangle(x, y, endX - x, endY - y);
  }

  public static boolean isInsideMarkerArea(@NotNull MouseEvent e) {
    final EditorGutterComponentEx gutter = (EditorGutterComponentEx)e.getComponent();
    return e.getX() > gutter.getLineMarkerFreePaintersAreaOffset();
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


  private class MyActiveGutterRenderer implements ActiveGutterRenderer {
    @NotNull private final Range myRange;

    public MyActiveGutterRenderer(@NotNull Range range) {
      myRange = range;
    }

    @Override
    public void paint(Editor editor, Graphics g, Rectangle r) {
      LineStatusMarkerRenderer.this.paint(editor, myRange, g);
    }

    @Override
    public boolean canDoAction(MouseEvent e) {
      return LineStatusMarkerRenderer.this.canDoAction(myRange, e);
    }

    @Override
    public void doAction(Editor editor, MouseEvent e) {
      LineStatusMarkerRenderer.this.doAction(editor, myRange, e);
    }
  }
}
