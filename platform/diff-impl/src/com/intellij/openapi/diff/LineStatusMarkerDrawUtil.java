// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff;

import com.intellij.diff.util.DiffDrawUtil;
import com.intellij.diff.util.DiffUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vcs.ex.*;
import com.intellij.openapi.vcs.ex.VisibleRangeMerger.FlagsProvider;
import com.intellij.ui.paint.LinePainter2D;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.IntPair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.List;

import static com.intellij.diff.util.DiffDrawUtil.lineToY;

public class LineStatusMarkerDrawUtil {
  @NotNull
  public static RangeHighlighter createTooltipRangeHighlighter(@NotNull Range range,
                                                               @NotNull MarkupModel markupModel) {
    TextRange textRange = DiffUtil.getLinesRange(markupModel.getDocument(), range.getLine1(), range.getLine2(), false);
    TextAttributes attributes = new TextAttributes() {
      @Override
      public Color getErrorStripeColor() {
        return LineStatusMarkerDrawUtil.getErrorStripeColor(range);
      }
    };

    RangeHighlighter highlighter = markupModel.addRangeHighlighter(textRange.getStartOffset(), textRange.getEndOffset(),
                                                                   DiffDrawUtil.LST_LINE_MARKER_LAYER, attributes,
                                                                   HighlighterTargetArea.LINES_IN_RANGE);
    highlighter.setThinErrorStripeMark(true);
    highlighter.setGreedyToLeft(true);
    highlighter.setGreedyToRight(true);

    return highlighter;
  }


  public static void paintDefault(@NotNull Editor editor,
                                  @NotNull Graphics g,
                                  @NotNull LineStatusTrackerI<?> tracker,
                                  @NotNull FlagsProvider<DefaultLineFlags> flagsProvider,
                                  int framingBorder) {
    List<? extends Range> ranges = tracker.getRanges();
    if (ranges == null) return;

    List<ChangesBlock<DefaultLineFlags>> blocks = VisibleRangeMerger.merge(editor, ranges, flagsProvider, g.getClipBounds());
    for (ChangesBlock<DefaultLineFlags> block : blocks) {
      paintChangedLines((Graphics2D)g, editor, block.changes, framingBorder);
    }
  }

  private static void paintChangedLines(@NotNull Graphics2D g,
                                        @NotNull Editor editor,
                                        @NotNull List<? extends ChangedLines<DefaultLineFlags>> block,
                                        int framingBorder) {
    EditorImpl editorImpl = (EditorImpl)editor;

    Color borderColor = getGutterBorderColor(editor);
    Color gutterBackgroundColor = ((EditorEx)editor).getGutterComponentEx().getBackground();

    IntPair area = getGutterArea(editor);
    final int x = area.first;
    final int endX = area.second;

    final int y = block.get(0).y1;
    final int endY = block.get(block.size() - 1).y2;


    if (framingBorder > 0) {
      if (y != endY) {
        g.setColor(gutterBackgroundColor);
        g.fillRect(x - framingBorder, y - framingBorder,
                   endX - x + framingBorder, endY - y + framingBorder * 2);
      }
    }

    for (ChangedLines<DefaultLineFlags> change : block) {
      if (change.y1 != change.y2 &&
          !change.flags.isIgnored) {
        int start = change.y1;
        int end = change.y2;
        Color gutterColor = getGutterColor(change.type, editor);
        paintRect(g, gutterColor, null, x, start, endX, end);
      }
    }

    if (borderColor == null) {
      for (ChangedLines<DefaultLineFlags> change : block) {
        if (change.y1 != change.y2 &&
            change.flags.isIgnored) {
          int start = change.y1;
          int end = change.y2;
          Color ignoredBorderColor = getIgnoredGutterBorderColor(change.type, editor);
          paintRect(g, null, ignoredBorderColor, x, start, endX, end);
        }
      }
    }
    else if (y != endY) {
      paintRect(g, null, borderColor, x, y, endX, endY);
    }

    for (ChangedLines<DefaultLineFlags> change : block) {
      if (change.y1 == change.y2) {
        int start = change.y1;
        if (!change.flags.isIgnored) {
          Color gutterColor = getGutterColor(change.type, editor);
          paintTriangle(g, editor, gutterColor, borderColor, x, endX, start);
        }
        else if (borderColor != null) {
          paintTriangle(g, editor, null, borderColor, x, endX, start);
        }
        else {
          Color ignoredBorderColor = getIgnoredGutterBorderColor(change.type, editor);
          paintTriangle(g, editor, null, ignoredBorderColor, x, endX, start);
        }
      }
    }
  }

  public static void paintRange(@NotNull Graphics g,
                                @NotNull Editor editor,
                                @NotNull Range range,
                                int framingBorder,
                                boolean isIgnored) {
    FlagsProvider<DefaultLineFlags> flagsProvider = isIgnored ? DefaultFlagsProvider.ALL_IGNORED : DefaultFlagsProvider.DEFAULT;
    List<ChangesBlock<DefaultLineFlags>> blocks = VisibleRangeMerger.merge(editor, Collections.singletonList(range), flagsProvider,
                                                                           g.getClipBounds());
    for (ChangesBlock<DefaultLineFlags> block : blocks) {
      paintChangedLines((Graphics2D)g, editor, block.changes, framingBorder);
    }
  }

  public static void paintSimpleRange(Graphics g, Editor editor, int line1, int line2, @Nullable Color color) {
    IntPair horizontalArea = getGutterArea(editor);
    int x = horizontalArea.first;
    int endX = horizontalArea.second;

    int y = lineToY(editor, line1);
    int endY = lineToY(editor, line2);

    Color borderColor = getGutterBorderColor(editor);
    if (endY != y) {
      paintRect((Graphics2D)g, color, borderColor, x, y, endX, endY);
    }
    else {
      paintTriangle((Graphics2D)g, editor, color, borderColor, x, endX, y);
    }
  }

  @NotNull
  public static IntPair getGutterArea(@NotNull Editor editor) {
    EditorGutterComponentEx gutter = ((EditorEx)editor).getGutterComponentEx();
    int x = gutter.getLineMarkerFreePaintersAreaOffset() + 1; // leave 1px for brace highlighters
    int endX = gutter.getWhitespaceSeparatorOffset();
    return new IntPair(x, endX);
  }

  public static boolean isInsideMarkerArea(@NotNull MouseEvent e) {
    final EditorGutterComponentEx gutter = (EditorGutterComponentEx)e.getComponent();
    return e.getX() > gutter.getLineMarkerFreePaintersAreaOffset();
  }

  public static void paintRect(@NotNull Graphics2D g, @Nullable Color color, @Nullable Color borderColor,
                               int x1, int y1, int x2, int y2) {
    if (color != null) {
      g.setColor(color);
      g.fillRect(x1, y1, x2 - x1, y2 - y1);
    }
    if (borderColor != null) {
      Stroke oldStroke = g.getStroke();
      g.setStroke(new BasicStroke(JBUIScale.scale(1)));
      g.setColor(borderColor);
      LinePainter2D.paint(g, x1, y1, x2 - 1, y1);
      LinePainter2D.paint(g, x1, y1, x1, y2 - 1);
      LinePainter2D.paint(g, x1, y2 - 1, x2 - 1, y2 - 1);
      g.setStroke(oldStroke);
    }
  }

  public static void paintTriangle(@NotNull Graphics2D g, @NotNull Editor editor, @Nullable Color color, @Nullable Color borderColor,
                                   int x1, int x2, int y) {
    float editorScale = editor instanceof EditorImpl ? ((EditorImpl)editor).getScale() : 1.0f;
    int size = (int)JBUIScale.scale(4 * editorScale);
    if (y < size) y = size;

    final int[] xPoints = new int[]{x1, x1, x2};
    final int[] yPoints = new int[]{y - size, y + size, y};

    if (color != null) {
      g.setColor(color);
      g.fillPolygon(xPoints, yPoints, xPoints.length);
    }
    if (borderColor != null) {
      Stroke oldStroke = g.getStroke();
      g.setStroke(new BasicStroke(JBUIScale.scale(1)));
      g.setColor(borderColor);
      g.drawPolygon(xPoints, yPoints, xPoints.length);
      g.setStroke(oldStroke);
    }
  }

  @Nullable
  public static Color getGutterColor(byte type, @Nullable Editor editor) {
    final EditorColorsScheme scheme = getColorScheme(editor);
    switch (type) {
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
  public static Color getErrorStripeColor(@NotNull Range range) {
    final EditorColorsScheme scheme = getColorScheme(null);
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
  public static Color getIgnoredGutterBorderColor(byte type, @Nullable Editor editor) {
    final EditorColorsScheme scheme = getColorScheme(editor);
    switch (type) {
      case Range.INSERTED:
        return scheme.getColor(EditorColors.IGNORED_ADDED_LINES_BORDER_COLOR);
      case Range.DELETED:
        return scheme.getColor(EditorColors.IGNORED_DELETED_LINES_BORDER_COLOR);
      case Range.MODIFIED:
      case Range.EQUAL:
        return scheme.getColor(EditorColors.IGNORED_MODIFIED_LINES_BORDER_COLOR);
      default:
        assert false;
        return null;
    }
  }

  @Nullable
  public static Color getGutterBorderColor(@Nullable Editor editor) {
    return getColorScheme(editor).getColor(EditorColors.BORDER_LINES_COLOR);
  }

  @NotNull
  private static EditorColorsScheme getColorScheme(@Nullable Editor editor) {
    return editor != null ? editor.getColorsScheme() : EditorColorsManager.getInstance().getGlobalScheme();
  }
}
