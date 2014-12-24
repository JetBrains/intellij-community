/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.util.diff.tools.util;

import com.intellij.openapi.diff.impl.splitter.Transformation;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.util.diff.util.DiffDrawUtil;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * A polygon, which is drawn between editors in merge or diff dialogs, and which indicates the change flow from one editor to another.
 */
public class DividerPolygon {
  @NotNull private final Color myColor;
  private final int myStart1;
  private final int myStart2;
  private final int myEnd1;
  private final int myEnd2;

  public DividerPolygon(int start1, int start2, int end1, int end2, @NotNull Color color) {
    myStart1 = start1;
    myStart2 = start2;
    myEnd1 = end1;
    myEnd2 = end2;
    myColor = color;
  }

  private void paint(Graphics2D g, int width) {
    DiffDrawUtil.drawCurveTrapezium(g, 0, width, myStart1, myEnd1, myStart2, myEnd2, myColor);
  }

  private void paintSimple(Graphics2D g, int width) {
    DiffDrawUtil.drawTrapezium(g, 0, width, myStart1, myEnd1, myStart2, myEnd2, myColor);
  }

  public String toString() {
    return "<" + myStart1 + ", " + myEnd1 + " : " + myStart2 + ", " + myEnd2 + "> " + myColor;
  }

  public static void paintPolygons(@NotNull Graphics2D g, @NotNull List<DividerPolygon> polygons, int width) {
    GraphicsUtil.setupAAPainting(g);

    for (DividerPolygon polygon : polygons) {
      polygon.paint(g, width);
    }
  }

  public static void paintSimplePolygons(@NotNull Graphics2D g, @NotNull List<DividerPolygon> polygons, int width) {
    GraphicsUtil.setupAAPainting(g);

    for (DividerPolygon polygon : polygons) {
      polygon.paintSimple(g, width);
    }
  }

  public static void paintPolygonsOnScrollbar(@NotNull Graphics2D g, @NotNull EditorEx editor, @NotNull List<DividerPolygon> polygons) {
    for (DividerPolygon polygon : polygons) {
      int startY = polygon.myStart1;
      int endY = polygon.myEnd1;
      int height = endY - startY;

      int scrollbarWidth = editor.getScrollPane().getVerticalScrollBar().getWidth();
      int startX = 0;
      int endX = startX + scrollbarWidth - 1;

      Color color = polygon.myColor;

      g.setColor(color);
      if (height > 2) {
        g.fillRect(startX, startY, scrollbarWidth, height);

        Color framingColor = DiffDrawUtil.getFramingColor(color);
        UIUtil.drawLine(g, startX, startY, endX, startY, null, framingColor);
        UIUtil.drawLine(g, startX, endY, endX, endY, null, framingColor);
      }
      else {
        DiffDrawUtil.drawDoubleShadowedLine(g, startX, endX, startY, color);
      }
    }
  }

  @NotNull
  public static List<DividerPolygon> createVisiblePolygons(@NotNull Editor editor1,
                                                           @NotNull Editor editor2,
                                                           @NotNull DividerPaintable paintable) {
    final List<DividerPolygon> polygons = new ArrayList<DividerPolygon>();

    final Transformation[] transformations = new Transformation[]{getTransformation(editor1), getTransformation(editor2)};

    final Interval leftInterval = getVisibleInterval(editor1);
    final Interval rightInterval = getVisibleInterval(editor2);

    paintable.process(new DividerPaintable.Handler() {
      @Override
      public boolean process(int startLine1, int endLine1, int startLine2, int endLine2, @NotNull Color color) {
        if (leftInterval.startLine > endLine1 && rightInterval.startLine > endLine2) return true;
        if (leftInterval.endLine < startLine1 && rightInterval.endLine < startLine2) return false;

        polygons.add(createPolygon(transformations, startLine1, endLine1, startLine2, endLine2, color));
        return true;
      }
    });

    return polygons;
  }

  private static Transformation getTransformation(@NotNull final Editor editor) {
    return new Transformation() {
      @Override
      public int transform(int line) {
        int yOffset = editor.logicalPositionToXY(new LogicalPosition(line, 0)).y;
        yOffset--; // hack: we need this shift, because top line of Editor is not visible.

        final JComponent header = editor.getHeaderComponent();
        int headerOffset = header == null ? 0 : header.getHeight();

        return yOffset - editor.getScrollingModel().getVerticalScrollOffset() + headerOffset;
      }
    };
    //return new FoldingTransformation(editor);
  }

  private static DividerPolygon createPolygon(@NotNull Transformation[] transformations,
                                              int startLine1, int endLine1,
                                              int startLine2, int endLine2,
                                              @NotNull Color color) {
    Transformation leftTransform = transformations[0];
    Transformation rightTransform = transformations[1];
    int start1 = leftTransform.transform(startLine1);
    int end1 = leftTransform.transform(endLine1);
    int start2 = rightTransform.transform(startLine2);
    int end2 = rightTransform.transform(endLine2);
    return new DividerPolygon(start1, start2, end1, end2, color);
  }

  @NotNull
  private static Interval getVisibleInterval(Editor editor) {
    Rectangle area = editor.getScrollingModel().getVisibleArea();
    LogicalPosition position1 = editor.xyToLogicalPosition(new Point(0, area.y));
    LogicalPosition position2 = editor.xyToLogicalPosition(new Point(0, area.y + area.height));
    return new Interval(position1.line, position2.line);
  }

  public interface DividerPaintable {
    void process(@NotNull Handler handler);

    interface Handler {
      boolean process(int startLine1, int endLine1, int startLine2, int endLine2, @NotNull Color color);
    }
  }

  private static class Interval {
    public final int startLine;
    public final int endLine;

    public Interval(int startLine, int endLine) {
      this.startLine = startLine;
      this.endLine = endLine;
    }
  }
}
