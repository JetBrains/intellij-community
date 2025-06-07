// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diff;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.ex.ChangedLines;
import com.intellij.openapi.vcs.ex.ChangesBlock;
import com.intellij.openapi.vcs.ex.Range;
import com.intellij.openapi.vcs.ex.VisibleRangeMerger;
import com.intellij.openapi.vcs.ex.VisibleRangeMerger.FlagsProvider;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.paint.LinePainter2D;
import com.intellij.ui.paint.PaintUtil;
import com.intellij.ui.paint.RectanglePainter2D;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.IntPair;
import com.intellij.util.ui.JBUI;
import kotlin.Unit;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.intellij.diff.util.DiffDrawUtil.lineToY;

/**
 * "Triangle" means the marker for the deleted block. In the new UI its shape is different
 */
public final class LineStatusMarkerDrawUtil {
  public static @NotNull List<Range> getSelectedRanges(@NotNull List<? extends Range> ranges, @NotNull Editor editor, int y) {
    int lineHeight = editor.getLineHeight();
    int triangleGap = getTriangleAimGap(editor);

    Rectangle clip = new Rectangle(0, y - lineHeight, editor.getComponent().getWidth(), lineHeight * 2);
    List<ChangesBlock<Unit>> blocks = VisibleRangeMerger.merge(editor, ranges, clip);

    List<Range> result = new ArrayList<>();
    for (ChangesBlock<Unit> block : blocks) {
      if (isBlockUnderY(block, y, triangleGap)) {
        result.addAll(block.ranges);
      }
    }
    return result;
  }

  private static boolean isBlockUnderY(ChangesBlock<?> block, int y, int triangleGap) {
    if (y == -1) return false;

    ChangedLines<?> firstChange = block.changes.get(0);
    ChangedLines<?> lastChange = block.changes.get(block.changes.size() - 1);

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
      return true;
    }
    return false;
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
                                  @NotNull List<? extends Range> ranges,
                                  @NotNull FlagsProvider<DefaultLineFlags> flagsProvider,
                                  int framingBorder) {
    paintDefault(editor, g, ranges, flagsProvider, LineStatusMarkerColorScheme.DEFAULT, framingBorder);
  }

  public static void paintDefault(@NotNull Editor editor,
                                  @NotNull Graphics g,
                                  @NotNull List<? extends Range> ranges,
                                  @NotNull FlagsProvider<DefaultLineFlags> flagsProvider,
                                  @NotNull LineStatusMarkerColorScheme colorScheme,
                                  int framingBorder) {
    List<ChangesBlock<DefaultLineFlags>> blocks = VisibleRangeMerger.merge(editor, ranges, flagsProvider, g.getClipBounds());
    EditorGutterComponentEx gutterComponentEx = ((EditorEx)editor).getGutterComponentEx();

    GroupedBlocks groupedBlocks = groupBlocks(editor, gutterComponentEx, blocks);

    for (ChangesBlock<DefaultLineFlags> block : groupedBlocks.unhoveredBlocks()) {
      paintChangedLines((Graphics2D)g, editor, block.changes, colorScheme, framingBorder, false);
    }

    for (ChangesBlock<DefaultLineFlags> block : groupedBlocks.hoveredBlocks()) {
      paintChangedLines((Graphics2D)g, editor, block.changes, colorScheme, framingBorder, true);
    }
  }

  @ApiStatus.Internal
  public static @NotNull LineStatusMarkerDrawUtil.GroupedBlocks groupBlocks(@NotNull Editor editor,
                                                                            EditorGutterComponentEx gutterComponentEx,
                                                                            List<ChangesBlock<DefaultLineFlags>> blocks) {
    List<ChangesBlock<DefaultLineFlags>> hoveredBlocks = new ArrayList<>();
    List<ChangesBlock<DefaultLineFlags>> unhoveredBlocks = new ArrayList<>();

    int triangleGap = getTriangleAimGap(editor);
    int y = gutterComponentEx.getHoveredFreeMarkersY();

    for (ChangesBlock<DefaultLineFlags> block : blocks) {
      if (isBlockUnderY(block, y, triangleGap)) {
        hoveredBlocks.add(block);
      }
      else {
        unhoveredBlocks.add(block);
      }
    }
    GroupedBlocks groupedBlocks = new GroupedBlocks(hoveredBlocks, unhoveredBlocks);
    return groupedBlocks;
  }

  @ApiStatus.Internal
  public record GroupedBlocks(List<ChangesBlock<DefaultLineFlags>> hoveredBlocks, List<ChangesBlock<DefaultLineFlags>> unhoveredBlocks) {
  }

  public static void paintChangedLines(@NotNull Graphics2D g,
                                       @NotNull Editor editor,
                                       @NotNull List<? extends ChangedLines<DefaultLineFlags>> block,
                                       int framingBorder,
                                       boolean isHovered) {
    paintChangedLines(g, editor, block, LineStatusMarkerColorScheme.DEFAULT, framingBorder, isHovered);
  }

  public static void paintChangedLines(@NotNull Graphics2D g,
                                       @NotNull Editor editor,
                                       @NotNull List<? extends ChangedLines<DefaultLineFlags>> block,
                                       @NotNull LineStatusMarkerColorScheme colorScheme,
                                       int framingBorder,
                                       boolean isHovered) {
    Color borderColor = LineStatusMarkerColorScheme.DEFAULT.getBorderColor(editor);
    EditorGutterComponentEx gutter = ((EditorEx)editor).getGutterComponentEx();
    Color gutterBackgroundColor = gutter.getBackground();

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
        Color gutterColor = colorScheme.getColor(editor, change.type);
        int x1 = isHovered ? x - getHoveredMarkerExtraWidth() : x;
        paintRect(g, gutterColor, null, x1, start, endX, end);
      }
    }

    if (borderColor == null) {
      for (ChangedLines<DefaultLineFlags> change : block) {
        if (change.y1 != change.y2 &&
            change.flags.isIgnored) {
          int start = change.y1;
          int end = change.y2;
          Color ignoredBorderColor = colorScheme.getIgnoredBorderColor(editor, change.type);
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
          Color gutterColor = colorScheme.getColor(editor, change.type);
          int x1 = isHovered ? x - getHoveredMarkerExtraWidth() : x;
          paintTriangle(g, editor, gutterColor, borderColor, x1, endX, start);
        }
        else {
          Color ignoredBorderColor = borderColor != null ? borderColor : colorScheme.getIgnoredBorderColor(editor, change.type);
          paintTriangle(g, editor, null, ignoredBorderColor, x, endX, start);
        }
      }
    }
  }

  public static boolean isRangeHovered(@NotNull Editor editor, int line, int x, int start, int end) {
    return line != -1 &&
           editor.xyToLogicalPosition(new Point(x, start)).line <= line &&
           line < editor.xyToLogicalPosition(new Point(x, end)).line;
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
      paintChangedLines((Graphics2D)g, editor, block.changes, framingBorder, false);
    }
  }

  public static void paintSimpleRange(@NotNull Graphics g, @NotNull Editor editor, int line1, int line2, @Nullable Color color) {
    IntPair horizontalArea = getGutterArea(editor);
    int x = horizontalArea.first;
    int endX = horizontalArea.second;

    int y = lineToY(editor, line1);
    int endY = lineToY(editor, line2);

    Color borderColor = LineStatusMarkerColorScheme.DEFAULT.getBorderColor(editor);
    if (endY != y) {
      paintRect((Graphics2D)g, color, borderColor, x, y, endX, endY);
    }
    else {
      paintTriangle((Graphics2D)g, editor, color, borderColor, x, endX, y);
    }
  }

  public static @NotNull IntPair getGutterArea(@NotNull Editor editor) {
    EditorGutterComponentEx gutter = ((EditorEx)editor).getGutterComponentEx();
    if (ExperimentalUI.isNewUI()) {
      int x = gutter.getExtraLineMarkerFreePaintersAreaOffset();
      int width = Registry.intValue("gutter.vcs.changes.width", 4, 4, 6);
      x += JBUI.scale(1); // leave 1px for brace highlighters
      if (width < 5) {
        x += JBUI.scale(2); //IDEA-286352
      }
      int areaWidth = scaleWithEditor(JBUIScale.scale(JBUI.getInt("Gutter.VcsChanges.width", width)), editor);
      return new IntPair(x, x + areaWidth);
    }
    else {
      int x = gutter.getLineMarkerFreePaintersAreaOffset();
      x += 1; // leave 1px for brace highlighters
      int endX = gutter.getWhitespaceSeparatorOffset();
      return new IntPair(x, endX);
    }
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
      }
      else if (borderColor != null) {
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
    int size = getTriangleHeight(editor);
    if (y < size) y = size;

    if (ExperimentalUI.isNewUI()) {
      if (color != null) {
        g.setColor(color);
        double width = x2 - x1;
        RectanglePainter2D.FILL.paint(g, x1, y - size + 1, width, 2 * size - 2, width);
      }
      else if (borderColor != null) {
        g.setColor(borderColor);
        double width = x2 - x1;
        RectanglePainter2D.DRAW.paint(g, x1, y - size + 1, width, 2 * size - 2, width);
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

  private static int getTriangleHeight(@NotNull Editor editor) {
    int unscaled = ExperimentalUI.isNewUI() ? 5 : 4;
    return scaleWithEditor(unscaled, editor);
  }

  private static int getTriangleAimGap(@NotNull Editor editor) {
    return editor.getLineHeight() / 3;
  }

  /**
   * Used only for the new UI
   *
   * @see EditorGutterComponentImpl#updateFreePainters
   */
  private static int getHoveredMarkerExtraWidth() {
    return JBUI.scale(3);
  }

  private static int scaleWithEditor(float v, @NotNull Editor editor) {
    float scale = editor instanceof EditorImpl ? ((EditorImpl)editor).getScale() : 1.0f;
    return PaintUtil.RoundingMode.ROUND.round(v * scale);
  }

  public static @Nullable Color getErrorStripeColor(byte type) {
    return LineStatusMarkerColorScheme.DEFAULT.getErrorStripeColor(type);
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
