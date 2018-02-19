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

import com.intellij.codeInsight.folding.impl.FoldingUtil;
import com.intellij.openapi.diff.impl.splitter.Transformation;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.ui.GraphicsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class DiffDividerDrawUtil {
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
    final List<DividerPolygon> polygons = new ArrayList<>();

    final Transformation[] transformations = new Transformation[]{getTransformation(editor1), getTransformation(editor2)};

    final LineRange leftInterval = getVisibleInterval(editor1);
    final LineRange rightInterval = getVisibleInterval(editor2);

    paintable.process(new DividerPaintable.Handler() {
      @Override
      public boolean process(int startLine1, int endLine1, int startLine2, int endLine2,
                             @Nullable Color fillColor, @Nullable Color borderColor, boolean dottedBorder) {
        if (leftInterval.start > endLine1 && rightInterval.start > endLine2) return true;
        if (leftInterval.end < startLine1 && rightInterval.end < startLine2) return false;

        if (isIntervalVisible(editor1, startLine1, endLine1) ||
            isIntervalVisible(editor2, startLine2, endLine2)) {
          polygons.add(createPolygon(transformations, startLine1, endLine1, startLine2, endLine2, fillColor, borderColor, dottedBorder));
        }
        return true;
      }
    });

    return polygons;
  }

  private static boolean isIntervalVisible(@NotNull Editor editor, int startLine, int endLine) {
    TextRange range = DiffUtil.getLinesRange(editor.getDocument(), startLine, endLine);
    return !FoldingUtil.isTextRangeFolded(editor, range);
  }

  @NotNull
  public static List<DividerSeparator> createVisibleSeparators(@NotNull Editor editor1,
                                                               @NotNull Editor editor2,
                                                               @NotNull DividerSeparatorPaintable paintable) {
    final List<DividerSeparator> separators = new ArrayList<>();

    final Transformation[] transformations = new Transformation[]{getTransformation(editor1), getTransformation(editor2)};

    final LineRange leftInterval = getVisibleInterval(editor1);
    final LineRange rightInterval = getVisibleInterval(editor2);

    final int height1 = editor1.getLineHeight();
    final int height2 = editor2.getLineHeight();

    final EditorColorsScheme scheme = editor1.getColorsScheme();

    paintable.process((line1, line2) -> {
      if (leftInterval.start > line1 + 1 && rightInterval.start > line2 + 1) return true;
      if (leftInterval.end < line1 && rightInterval.end < line2) return false;

      separators.add(createSeparator(transformations, line1, line2, height1, height2, scheme));
      return true;
    });

    return separators;
  }

  @NotNull
  private static Transformation getTransformation(@NotNull final Editor editor) {
    return (line) -> {
      int yOffset = editor.logicalPositionToXY(new LogicalPosition(line, 0)).y;

      final JComponent header = editor.getHeaderComponent();
      int headerOffset = header == null ? 0 : header.getHeight();

      return yOffset - editor.getScrollingModel().getVerticalScrollOffset() + headerOffset;
    };
  }

  @NotNull
  private static DividerPolygon createPolygon(@NotNull Transformation[] transformations,
                                              int startLine1, int endLine1,
                                              int startLine2, int endLine2,
                                              @Nullable Color fillColor, @Nullable Color borderColor, boolean dottedBorder) {
    int start1 = transformations[0].transform(startLine1);
    int end1 = transformations[0].transform(endLine1);
    int start2 = transformations[1].transform(startLine2);
    int end2 = transformations[1].transform(endLine2);
    return new DividerPolygon(start1, start2, end1, end2, fillColor, borderColor, dottedBorder);
  }

  @NotNull
  private static DividerSeparator createSeparator(@NotNull Transformation[] transformations,
                                                  int line1, int line2, int height1, int height2,
                                                  @Nullable EditorColorsScheme scheme) {
    int start1 = transformations[0].transform(line1);
    int start2 = transformations[1].transform(line2);
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

    abstract class Handler {
      public boolean process(int startLine1, int endLine1, int startLine2, int endLine2, @NotNull Color color) {
        return process(startLine1, endLine1, startLine2, endLine2, color, null, false);
      }

      public boolean processResolvable(int startLine1, int endLine1, int startLine2, int endLine2,
                                       @NotNull Editor editor, @NotNull TextDiffType type, boolean resolved) {
        Color color = type.getColor(editor);
        return process(startLine1, endLine1, startLine2, endLine2, resolved ? null : color, resolved ? color : null, resolved);
      }

      public boolean processExcludable(int startLine1, int endLine1, int startLine2, int endLine2,
                                       @NotNull Editor editor, @NotNull TextDiffType type, boolean excluded) {
        Color borderColor = excluded ? type.getColor(editor) : null;
        Color fillColor = excluded ? type.getIgnoredColor(editor) : type.getColor(editor);
        return process(startLine1, endLine1, startLine2, endLine2, fillColor, borderColor, false);
      }

      public abstract boolean process(int startLine1, int endLine1, int startLine2, int endLine2,
                                      @Nullable Color backgroundColor, @Nullable Color borderColor, boolean dottedBorder);
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

      if (curve) {
        DiffDrawUtil.drawCurveTrapezium(g, 0, width, startY1, endY1, startY2, endY2, myFillColor, myBorderColor);
      }
      else {
        DiffDrawUtil.drawTrapezium(g, 0, width, startY1, endY1, startY2, endY2, myFillColor, myBorderColor);
      }

      g.setStroke(oldStroke);
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
      DiffDrawUtil.drawConnectorLineSeparator(g, 0, width, myStart1, myEnd1, myStart2, myEnd2, myScheme);
    }

    public String toString() {
      return "<" + myStart1 + ", " + myEnd1 + " : " + myStart2 + ", " + myEnd2 + "> ";
    }
  }
}
