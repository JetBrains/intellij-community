// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.util;

import com.intellij.codeInsight.folding.impl.FoldingUtil;
import com.intellij.diff.util.DiffDrawUtil.MarkerRange;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.Interval;
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.GraphicsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.diff.util.DiffDrawUtil.lineToY;

public final class DiffDividerDrawUtil {
  public static final BasicStroke BOLD_DOTTED_STROKE =
    new BasicStroke(2.3f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, new float[]{2, 2}, 0.0f);

  /*
   * Clip given graphics of divider component such that result graphics is aligned with base component by 'y' coordinate.
   */
  @NotNull
  public static Graphics2D getDividerGraphics(@NotNull Graphics g, @NotNull Component divider, @NotNull Component base) {
    int width = divider.getWidth();
    int editorHeight = base.getHeight();
    int dividerOffset = divider.getLocationOnScreen().y;
    int editorOffset = base.getLocationOnScreen().y;
    return (Graphics2D)g.create(0, editorOffset - dividerOffset, width, editorHeight);
  }

  public static void paintSeparators(@NotNull Graphics2D gg,
                                     int width,
                                     @NotNull Editor editor1,
                                     @NotNull Editor editor2,
                                     @NotNull DividerSeparatorPaintable paintable) {
    List<DividerSeparator> polygons = createVisibleSeparators(editor1, editor2, paintable);

    GraphicsConfig config = GraphicsUtil.setupAAPainting(gg);
    for (DividerSeparator polygon : polygons) {
      polygon.paint(gg, width);
    }
    config.restore();
  }

  public static void paintPolygons(@NotNull Graphics2D gg,
                                   int width,
                                   @NotNull Editor editor1,
                                   @NotNull Editor editor2,
                                   @NotNull DividerPaintable paintable) {
    paintPolygons(gg, width, true, editor1, editor2, paintable);
  }

  public static void paintPolygons(@NotNull Graphics2D gg,
                                   int width,
                                   boolean curved,
                                   @NotNull Editor editor1,
                                   @NotNull Editor editor2,
                                   @NotNull DividerPaintable paintable) {
    List<DividerPolygon> polygons = createVisiblePolygons(editor1, editor2, paintable);

    GraphicsConfig config = GraphicsUtil.setupAAPainting(gg);
    for (DividerPolygon polygon : polygons) {
      polygon.paint(gg, width, curved);
    }
    config.restore();
  }

  @NotNull
  public static List<DividerPolygon> createVisiblePolygons(@NotNull Editor editor1,
                                                           @NotNull Editor editor2,
                                                           @NotNull DividerPaintable paintable) {
    DividerPaintableHandlerImpl handler = new DividerPaintableHandlerImpl(editor1, editor2);
    paintable.process(handler);
    return handler.getPolygons();
  }

  private static boolean isIntervalFolded(@NotNull Editor editor, int startLine, int endLine) {
    TextRange range = DiffUtil.getLinesRange(editor.getDocument(), startLine, endLine);
    return FoldingUtil.isTextRangeFolded(editor, range);
  }

  @NotNull
  public static List<DividerSeparator> createVisibleSeparators(@NotNull Editor editor1,
                                                               @NotNull Editor editor2,
                                                               @NotNull DividerSeparatorPaintable paintable) {
    final List<DividerSeparator> separators = new ArrayList<>();

    final LineRange leftInterval = getVisibleInterval(editor1);
    final LineRange rightInterval = getVisibleInterval(editor2);

    final int height1 = editor1.getLineHeight();
    final int height2 = editor2.getLineHeight();

    final EditorColorsScheme scheme = editor1.getColorsScheme();

    paintable.process((line1, line2) -> {
      if (leftInterval.start > line1 + 1 && rightInterval.start > line2 + 1) return true;
      if (leftInterval.end < line1 && rightInterval.end < line2) return false;

      separators.add(createSeparator(editor1, editor2, line1, line2, height1, height2, scheme));
      return true;
    });

    return separators;
  }

  private static int getEditorTopOffset(@NotNull final Editor editor) {
    final JComponent header = editor.getHeaderComponent();
    int headerOffset = header == null ? 0 : header.getHeight();
    return -editor.getScrollingModel().getVerticalScrollOffset() + headerOffset;
  }

  /**
   * Use CustomFoldRegion-friendly renderer for folded lines.
   * We do not use it by default to avoid potential inconsistency with {@link DiffLineMarkerRenderer}.
   *
   * @see DiffDrawUtil#getGutterMarkerPaintRange
   */
  private static MarkerRange getDividerMarkerPaintRange(@NotNull Editor editor, int startLine, int endLine) {
    Pair<@NotNull Interval, @Nullable Interval> pair1 = EditorUtil.logicalLineToYRange(editor, startLine);
    Pair<@NotNull Interval, @Nullable Interval> pair2 = startLine == endLine ? pair1 : EditorUtil.logicalLineToYRange(editor, endLine - 1);
    int startOffset = pair1.first.intervalStart();
    int endOffset = pair2.first.intervalEnd();
    return new MarkerRange(startOffset, endOffset);
  }

  @NotNull
  private static DividerSeparator createSeparator(@NotNull Editor editor1, @NotNull Editor editor2,
                                                  int line1, int line2, int height1, int height2,
                                                  @Nullable EditorColorsScheme scheme) {
    int topOffset1 = getEditorTopOffset(editor1);
    int topOffset2 = getEditorTopOffset(editor2);
    int start1 = lineToY(editor1, line1) + topOffset1;
    int start2 = lineToY(editor2, line2) + topOffset2;
    return new DividerSeparator(start1, start2, start1 + height1, start2 + height2, scheme);
  }

  @NotNull
  private static LineRange getVisibleInterval(Editor editor) {
    Rectangle area = editor.getScrollingModel().getVisibleArea();
    if (area.height < 0) return new LineRange(0, 0);
    LogicalPosition position1 = editor.xyToLogicalPosition(new Point(0, area.y));
    LogicalPosition position2 = editor.xyToLogicalPosition(new Point(0, area.y + area.height));
    return new LineRange(position1.line, position2.line);
  }

  public interface DividerPaintable {
    void process(@NotNull Handler handler);

    interface Handler {
      boolean process(int startLine1, int endLine1, int startLine2, int endLine2,
                      @NotNull TextDiffType type);

      boolean processResolvable(int startLine1, int endLine1, int startLine2, int endLine2,
                                @NotNull TextDiffType type, boolean resolved);

      boolean processExcludable(int startLine1, int endLine1, int startLine2, int endLine2,
                                @NotNull TextDiffType type, boolean excluded, boolean skipped);

      boolean processAligned(int startLine1, int endLine1, int startLine2, int endLine2,
                             @NotNull TextDiffType type);
    }
  }

  private static class DividerPaintableHandlerImpl implements DividerPaintable.Handler {
    private final Editor myEditor1;
    private final Editor myEditor2;

    private final LineRange myLeftInterval;
    private final LineRange myRightInterval;
    private final List<DividerPolygon> myPolygons = new ArrayList<>();

    private DividerPaintableHandlerImpl(@NotNull Editor editor1,
                                        @NotNull Editor editor2) {
      myEditor1 = editor1;
      myEditor2 = editor2;
      myLeftInterval = getVisibleInterval(editor1);
      myRightInterval = getVisibleInterval(editor2);
    }

    @NotNull
    public List<DividerPolygon> getPolygons() {
      return myPolygons;
    }

    @Override
    public boolean process(int startLine1, int endLine1, int startLine2, int endLine2,
                           @NotNull TextDiffType type) {
      return process(startLine1, endLine1, startLine2, endLine2, new DefaultPainter(type));
    }

    @Override
    public boolean processResolvable(int startLine1, int endLine1, int startLine2, int endLine2,
                                     @NotNull TextDiffType type, boolean resolved) {
      return process(startLine1, endLine1, startLine2, endLine2, new ResolvablePainter(type, resolved));
    }

    @Override
    public boolean processExcludable(int startLine1, int endLine1, int startLine2, int endLine2,
                                     @NotNull TextDiffType type, boolean excluded, boolean skipped) {
      return process(startLine1, endLine1, startLine2, endLine2, new ExcludablePainter(type, excluded, skipped));
    }

    @Override
    public boolean processAligned(int startLine1, int endLine1, int startLine2, int endLine2,
                                  @NotNull TextDiffType type) {
      if (type == TextDiffType.INSERTED || type == TextDiffType.DELETED) {
        return process(startLine1, endLine1, startLine2, endLine2, new DefaultPainter(type), true);
      }
      else {
        return process(startLine1, endLine1, startLine2, endLine2, type);
      }
    }

    private boolean process(int startLine1, int endLine1, int startLine2, int endLine2,
                            @NotNull Painter painter) {
      return process(startLine1, endLine1, startLine2, endLine2, painter, false);
    }

    private boolean process(int startLine1, int endLine1, int startLine2, int endLine2,
                            @NotNull Painter painter, boolean withAlignedHeight) {
      if (myLeftInterval.start > endLine1 && myRightInterval.start > endLine2) return true;
      if (myLeftInterval.end < startLine1 && myRightInterval.end < startLine2) return false;

      DividerPolygon polygon = createPolygon(myEditor1, myEditor2, startLine1, endLine1, startLine2, endLine2, painter);
      if (withAlignedHeight && polygon != null) {
        int inlayOffset = getInlayOffset(myEditor1, myEditor2, startLine1, startLine2, painter.getType());
        polygon = polygon.withAlignedHeight(inlayOffset);
      }

      ContainerUtil.addIfNotNull(myPolygons, polygon);
      return true;
    }

    private static int getInlayOffset(@NotNull Editor editor1, @NotNull Editor editor2,
                                      int startLine1, int startLine2,
                                      @NotNull TextDiffType type) {
      if (type == TextDiffType.INSERTED) {
        return EditorUtil.getInlaysHeight(editor2, startLine2, true);
      }
      if (type == TextDiffType.DELETED) {
        return EditorUtil.getInlaysHeight(editor1, startLine1, true);
      }
      if (type == TextDiffType.MODIFIED) {
        return Math.max(EditorUtil.getInlaysHeight(editor1, startLine1, true),
                        EditorUtil.getInlaysHeight(editor2, startLine2, true));
      }

      return 0;
    }

    @Nullable
    private static DividerPolygon createPolygon(@NotNull Editor editor1, @NotNull Editor editor2,
                                                int startLine1, int endLine1,
                                                int startLine2, int endLine2,
                                                @NotNull Painter painter) {
      int topOffset1 = getEditorTopOffset(editor1);
      int topOffset2 = getEditorTopOffset(editor2);

      boolean isFolded1 = isIntervalFolded(editor1, startLine1, endLine1);
      boolean isFolded2 = isIntervalFolded(editor2, startLine2, endLine2);
      boolean isFolded = isFolded1 && isFolded2;

      // Hide folded changes from non-current changelist in Local Changes.
      if (isFolded && !painter.isAlwaysVisible()) return null;

      MarkerRange range1 = isFolded1 ? getDividerMarkerPaintRange(editor1, startLine1, endLine1)
                                     : DiffDrawUtil.getGutterMarkerPaintRange(editor1, startLine1, endLine1);
      MarkerRange range2 = isFolded2 ? getDividerMarkerPaintRange(editor2, startLine2, endLine2)
                                     : DiffDrawUtil.getGutterMarkerPaintRange(editor2, startLine2, endLine2);
      return new DividerPolygon(range1.y1 + topOffset1, range2.y1 + topOffset2,
                                range1.y2 + topOffset1, range2.y2 + topOffset2,
                                painter.getFillColor(editor2, isFolded),
                                painter.getBorderColor(editor2, isFolded),
                                painter.isDottedBorder());
    }

    @NotNull
    private static TextDiffType correctType(@NotNull TextDiffType type, boolean isFolded) {
      if (isFolded && (type == TextDiffType.DELETED || type == TextDiffType.INSERTED)) return TextDiffType.MODIFIED;
      return type;
    }

    private static class DefaultPainter implements Painter {
      private final TextDiffType myType;

      private DefaultPainter(@NotNull TextDiffType type) {
        myType = type;
      }

      @Override
      public @Nullable Color getFillColor(@NotNull Editor editor, boolean isFolded) {
        return correctType(myType, isFolded).getColor(editor);
      }

      @Override
      public @Nullable Color getBorderColor(@NotNull Editor editor, boolean isFolded) {
        return null;
      }

      @Override
      public boolean isDottedBorder() {
        return false;
      }

      @Override
      public boolean isAlwaysVisible() {
        return true;
      }

      @Override
      public @NotNull TextDiffType getType() {
        return myType;
      }
    }

    private static class ResolvablePainter implements Painter {
      private final TextDiffType myType;
      private final boolean myResolved;

      private ResolvablePainter(@NotNull TextDiffType type, boolean resolved) {
        myType = type;
        myResolved = resolved;
      }

      @Override
      public @Nullable Color getFillColor(@NotNull Editor editor, boolean isFolded) {
        return !myResolved ? correctType(myType, isFolded).getColor(editor) : null;
      }

      @Override
      public @Nullable Color getBorderColor(@NotNull Editor editor, boolean isFolded) {
        return myResolved ? correctType(myType, isFolded).getColor(editor) : null;
      }

      @Override
      public boolean isDottedBorder() {
        return myResolved;
      }

      @Override
      public boolean isAlwaysVisible() {
        return !myResolved;
      }

      @Override
      public @NotNull TextDiffType getType() {
        return myType;
      }
    }

    private static class ExcludablePainter implements Painter {
      private final TextDiffType myType;
      private final boolean myExcluded;
      private final boolean mySkipped;

      private ExcludablePainter(@NotNull TextDiffType type, boolean excluded, boolean skipped) {
        myType = type;
        myExcluded = excluded;
        mySkipped = skipped;
      }

      @Override
      public @Nullable Color getFillColor(@NotNull Editor editor, boolean isFolded) {
        return myExcluded ? correctType(myType, isFolded).getIgnoredColor(editor)
                          : correctType(myType, isFolded).getColor(editor);
      }

      @Override
      public @Nullable Color getBorderColor(@NotNull Editor editor, boolean isFolded) {
        return myExcluded ? correctType(myType, isFolded).getColor(editor) : null;
      }

      @Override
      public boolean isDottedBorder() {
        return false;
      }

      @Override
      public boolean isAlwaysVisible() {
        return !mySkipped;
      }

      @Override
      public @NotNull TextDiffType getType() {
        return myType;
      }
    }

    private interface Painter {
      @Nullable Color getFillColor(@NotNull Editor editor, boolean isFolded);

      @Nullable Color getBorderColor(@NotNull Editor editor, boolean isFolded);

      boolean isDottedBorder();

      boolean isAlwaysVisible();

      @NotNull TextDiffType getType();
    }
  }

  public interface DividerSeparatorPaintable {
    void process(@NotNull Handler handler);

    interface Handler {
      boolean process(int line1, int line2);
    }
  }

  /**
   * A polygon, which is drawn between editors in merge or diff dialogs, and which indicates the change flow from one editor to another.
   */
  public static class DividerPolygon {
    // pixels from the top of editor
    private final int myStart1;
    private final int myStart2;
    private final int myEnd1;
    private final int myEnd2;
    @Nullable private final Color myFillColor;
    @Nullable private final Color myBorderColor;
    private final boolean myDottedBorder;

    public DividerPolygon(int start1, int start2, int end1, int end2,
                          @Nullable Color fillColor, @Nullable Color borderColor, boolean dottedBorder) {
      myStart1 = start1;
      myStart2 = start2;
      myEnd1 = end1;
      myEnd2 = end2;
      myFillColor = fillColor;
      myBorderColor = borderColor;
      myDottedBorder = dottedBorder;
    }

    public void paint(Graphics2D g, int width, boolean curve) {
      int startY1;
      int endY1;
      int startY2;
      int endY2;

      if (myEnd1 - myStart1 < 2) {
        startY1 = myStart1 - 1;
        endY1 = myStart1;
      }
      else {
        startY1 = myStart1;
        endY1 = myEnd1 - 1;
      }

      if (myEnd2 - myStart2 < 2) {
        startY2 = myStart2 - 1;
        endY2 = myStart2;
      }
      else {
        startY2 = myStart2;
        endY2 = myEnd2 - 1;
      }

      Stroke oldStroke = g.getStroke();
      if (myDottedBorder) {
        g.setStroke(BOLD_DOTTED_STROKE);
      }

      drawTrapezium(g, width, startY1, endY1, startY2, endY2, myFillColor, myBorderColor, curve);

      g.setStroke(oldStroke);
    }

    private static void drawTrapezium(@NotNull Graphics2D g,
                                      int width,
                                      int startY1, int endY1,
                                      int startY2, int endY2,
                                      @Nullable Color fillColor, @Nullable Color borderColor,
                                      boolean curve) {
      if (curve) {
        DiffDrawUtil.drawCurveTrapezium(g, 0, width, startY1, endY1, startY2, endY2, fillColor, borderColor);
      }
      else {
        DiffDrawUtil.drawTrapezium(g, 0, width, startY1, endY1, startY2, endY2, fillColor, borderColor);
      }
    }

    @NotNull
    public DividerPolygon withAlignedHeight(int inlayOffset) {
      int delta = (myEnd2 - myStart2) - (myEnd1 - myStart1);
      if (delta == 0) return this;

      if (myStart2 == myEnd1 && myEnd1 == myEnd2) { //correspond to the last line DELETED change (e.g. last line deleted)
        return new DividerPolygon(myStart1, myStart2 - (myEnd2 - myStart1), myEnd1, myEnd2, myFillColor, myBorderColor, myDottedBorder);
      }
      else if (myEnd1 == myEnd2 && myStart1 == myEnd1) { //correspond to the last line INSERTED change (e.g. added new lines after last line)
        return new DividerPolygon(myStart1 - (myEnd2 - myStart2), myStart2, myEnd1, myEnd2, myFillColor, myBorderColor, myDottedBorder);
      }
      if (delta < 0) {
        int startDelta = myStart2 == myEnd2 ? 0 : -delta;
        int endDelta = myStart2 == myEnd2 ? -delta : 0;
        return new DividerPolygon(myStart1, myStart2 - startDelta + inlayOffset,
                                  myEnd1, myEnd2 + endDelta + inlayOffset,
                                  myFillColor, myBorderColor, myDottedBorder);
      }
      else {
        int startDelta = myStart1 == myEnd1 ? 0 : delta;
        int endDelta = myStart1 == myEnd1 ? delta : 0;
        int firstLineOffset = (myStart1 == myEnd1 && myStart2 == 0) ? -1 : 0;
        return new DividerPolygon(myStart1 - startDelta + firstLineOffset + inlayOffset, myStart2,
                                  myEnd1 + endDelta + firstLineOffset + inlayOffset, myEnd2,
                                  myFillColor, myBorderColor, myDottedBorder);
      }
    }

    public String toString() {
      return "<" + myStart1 + ", " + myEnd1 + " : " + myStart2 + ", " + myEnd2 + "> " + myFillColor + ", " + myBorderColor;
    }
  }

  public static class DividerSeparator {
    // pixels from the top of editor
    private final int myStart1;
    private final int myStart2;
    private final int myEnd1;
    private final int myEnd2;
    @Nullable private final EditorColorsScheme myScheme;

    public DividerSeparator(int start1, int start2, int end1, int end2, @Nullable EditorColorsScheme scheme) {
      myStart1 = start1;
      myStart2 = start2;
      myEnd1 = end1;
      myEnd2 = end2;
      myScheme = scheme;
    }

    public void paint(Graphics2D g, int width) {
      DiffLineSeparatorRenderer.drawConnectorLine(g, 0, width, myStart1, myStart2, myEnd1 - myStart1, myScheme);
    }

    public String toString() {
      return "<" + myStart1 + ", " + myEnd1 + " : " + myStart2 + ", " + myEnd2 + "> ";
    }
  }
}
