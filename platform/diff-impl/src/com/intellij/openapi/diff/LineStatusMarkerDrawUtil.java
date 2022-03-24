// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diff;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.vcs.ex.*;
import com.intellij.openapi.vcs.ex.VisibleRangeMerger.FlagsProvider;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.paint.LinePainter2D;
import com.intellij.ui.paint.RectanglePainter2D;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.IntPair;
import com.intellij.util.ui.JBUI;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.intellij.diff.util.DiffDrawUtil.lineToY;

public class LineStatusMarkerDrawUtil {
  @NotNull
  public static List<Range> getSelectedRanges(@NotNull List<? extends Range> ranges, @NotNull Editor editor, int y) {
    int lineHeight = editor.getLineHeight();
    int triangleGap = lineHeight / 3;

    Rectangle clip = new Rectangle(0, y - lineHeight, editor.getComponent().getWidth(), lineHeight * 2);
    List<ChangesBlock<Unit>> blocks = VisibleRangeMerger.merge(editor, ranges, clip);

    List<Range> result = new ArrayList<>();
    for (ChangesBlock<Unit> block : blocks) {
      ChangedLines<Unit> firstChange = block.changes.get(0);
      ChangedLines<Unit> lastChange = block.changes.get(block.changes.size() - 1);

      int startY = firstChange.y1;
      int endY = lastChange.y2;

      // "empty" range for deleted block
      if (firstChange.y1 == firstChange.y2) {
        startY -= triangleGap;
      }
      if (lastChange.y1 == lastChange.y2) {
        endY += triangleGap;
      }

      if (startY <= y && endY > y) {
        result.addAll(block.ranges);
      }
    }
    return result;
  }

  public static Rectangle calcBounds(@NotNull List<? extends Range> ranges, @NotNull Editor editor, int lineNum) {
    int yStart = editor.visualLineToY(lineNum);
    Rectangle clip = new Rectangle(0, yStart, 0, editor.getLineHeight());

    List<ChangesBlock<Unit>> blocks = VisibleRangeMerger.merge(editor, ranges, clip);
    if (blocks.isEmpty()) return null;

    List<ChangedLines<Unit>> changes = blocks.get(0).changes;
    int y = changes.get(0).y1;
    int endY = changes.get(changes.size() - 1).y2;
    if (y == endY) {
      endY += editor.getLineHeight();
    }

    IntPair area = getGutterArea(editor);
    return new Rectangle(area.first, y, area.second - area.first, endY - y);
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

  public static void paintChangedLines(@NotNull Graphics2D g,
                                       @NotNull Editor editor,
                                       @NotNull List<? extends ChangedLines<DefaultLineFlags>> block,
                                       int framingBorder) {
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
        else {
          Color ignoredBorderColor = borderColor != null ? borderColor : getIgnoredGutterBorderColor(change.type, editor);
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
    if (ExperimentalUI.isNewUI()) {
      return new IntPair(x, x + (int)(JBUIScale.scale(JBUI.getInt("Gutter.VcsChanges.width", 4) * getEditorScale(editor))));
    }
    int endX = gutter.getWhitespaceSeparatorOffset();
    return new IntPair(x, endX);
  }

  public static boolean isInsideMarkerArea(@NotNull MouseEvent e) {
    final EditorGutterComponentEx gutter = (EditorGutterComponentEx)e.getComponent();
    return gutter.isInsideMarkerArea(e);
  }

  public static void paintRect(@NotNull Graphics2D g, @Nullable Color color, @Nullable Color borderColor,
                               int x1, int y1, int x2, int y2) {
    if (ExperimentalUI.isNewUI()) {
      if (color != null) {
        g.setColor(color);
        double width = x2 - x1;
        RectanglePainter2D.FILL.paint(g, x1, y1 + 1, width, y2 - y1 - 2, width);
      } else if (borderColor != null) {
        g.setColor(borderColor);
        double width = x2 - x1;
        RectanglePainter2D.DRAW.paint(g, x1, y1 + 1, width, y2 - y1 - 2, width);
      }
      return;
    }
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
    int size = (int)JBUIScale.scale(4 * getEditorScale(editor));
    if (y < size) y = size;

    if (ExperimentalUI.isNewUI()) {
      if (color != null) {
        g.setColor(color);
        double width = x2 - x1;
        RectanglePainter2D.FILL.paint(g, x1, y - size + 1, width, 2 * size - 2, width);
      }
      return;
    }

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

  private static float getEditorScale(@NotNull Editor editor) {
    return editor instanceof EditorImpl ? ((EditorImpl)editor).getScale() : 1.0f;
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
    return getErrorStripeColor(range.getType());
  }

  @Nullable
  public static Color getErrorStripeColor(byte type) {
    final EditorColorsScheme scheme = getColorScheme(null);
    switch (type) {
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

  public static class DiffStripeTextAttributes extends TextAttributes {
    private final byte myType;

    public DiffStripeTextAttributes(byte type) {
      myType = type;
    }

    public byte getType() {
      return myType;
    }

    @Override
    public Color getErrorStripeColor() {
      return LineStatusMarkerDrawUtil.getErrorStripeColor(myType);
    }
  }
}
