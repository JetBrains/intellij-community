// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.hover;

import com.intellij.openapi.util.Key;
import com.intellij.ui.render.RenderingUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.JViewport;
import java.awt.Component;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.ToIntFunction;

@ApiStatus.Experimental
public abstract class TableHoverListener extends HoverListener {
  public abstract void onHover(@NotNull JTable table, int row, int column);

  @Override
  public final void mouseEntered(@NotNull Component component, int x, int y) {
    mouseMoved(component, x, y);
  }

  @Override
  public final void mouseMoved(@NotNull Component component, int x, int y) {
    update(component, table -> table.rowAtPoint(new Point(x, y)), table -> table.columnAtPoint(new Point(x, y)));
  }

  @Override
  public final void mouseExited(@NotNull Component component) {
    update(component, table -> -1, table -> -1);
  }


  private final AtomicInteger rowHolder = new AtomicInteger(-1);
  private final AtomicInteger columnHolder = new AtomicInteger(-1);

  private void update(@NotNull Component component, @NotNull ToIntFunction<? super JTable> rowFunc, @NotNull ToIntFunction<? super JTable> columnFunc) {
    if (component instanceof JTable) {
      JTable table = (JTable)component;
      int rowNew = rowFunc.applyAsInt(table);
      int rowOld = rowHolder.getAndSet(rowNew);
      int columnNew = columnFunc.applyAsInt(table);
      int columnOld = columnHolder.getAndSet(columnNew);
      if (rowNew != rowOld || columnNew != columnOld) onHover(table, rowNew, columnNew);
    }
  }


  private static final Key<Integer> HOVERED_ROW_KEY = Key.create("TableHoveredRow");
  public static final HoverListener DEFAULT = new TableHoverListener() {
    @Override
    public void onHover(@NotNull JTable table, int row, int column) {
      setHoveredRow(table, row);
      // support JBTreeTable and similar views
      Object property = table.getClientProperty(RenderingUtil.FOCUSABLE_SIBLING);
      if (property instanceof JTree) TreeHoverListener.setHoveredRow((JTree)property, row);
    }
  };

  @ApiStatus.Internal
  static void setHoveredRow(@NotNull JTable table, int rowNew) {
    int rowOld = getHoveredRow(table);
    if (rowNew == rowOld) return;
    table.putClientProperty(HOVERED_ROW_KEY, rowNew < 0 ? null : rowNew);
    // tables without scroll pane do not repaint rows correctly (BasicTableUI.paint:1868-1872)
    if (table.getParent() instanceof JViewport) {
      repaintRow(table, rowOld);
      repaintRow(table, rowNew);
    }
    else {
      table.repaint();
    }
  }

  /**
   * @param table a table, which hover state is interesting
   * @return a number of a hovered row of the specified table
   * @see #DEFAULT
   */
  public static int getHoveredRow(@NotNull JTable table) {
    Object property = table.getClientProperty(HOVERED_ROW_KEY);
    return property instanceof Integer ? (Integer)property : -1;
  }

  private static void repaintRow(@NotNull JTable table, int row) {
    Rectangle bounds = row < 0 ? null : table.getCellRect(row, 0, false);
    if (bounds != null) table.repaint(0, bounds.y, table.getWidth(), bounds.height);
  }
}
