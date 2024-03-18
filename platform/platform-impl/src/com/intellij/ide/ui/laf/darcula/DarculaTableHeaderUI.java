// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.darcula;

import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.ComponentWithExpandableItems;
import com.intellij.ui.JBColor;
import com.intellij.ui.TableUtil;
import com.intellij.ui.paint.LinePainter2D;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBValue;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicTableHeaderUI;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.util.Objects;

/**
 * @author Konstantin Bulenkov
 */
public class DarculaTableHeaderUI extends BasicTableHeaderUI {

  public static final String SKIP_DRAWING_VERTICAL_CELL_SEPARATOR_KEY = "TableHeaderUI.skipDrawingVerticalCellSeparator";

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "UnusedDeclaration"})
  public static ComponentUI createUI(JComponent c) {
    return new DarculaTableHeaderUI();
  }

  @Override
  public void paint(Graphics g2, JComponent c) {
    final boolean skipDrawingVerticalCellSeparator = Objects.equals(c.getClientProperty(SKIP_DRAWING_VERTICAL_CELL_SEPARATOR_KEY), true);
    final Graphics2D g = (Graphics2D)g2;
    final GraphicsConfig config = new GraphicsConfig(g);
    final Color bg = c.getBackground();
    g.setPaint(bg);

    TableColumnModel model = header.getColumnModel();

    final int h = header.getHeight();
    final int w = model.getTotalColumnWidth();
    g.fillRect(0, 0, w, h);
    JBColor bottomSeparatorColor = JBColor.namedColor("TableHeader.bottomSeparatorColor", ColorUtil.shift(bg, 0.75));
    g.setPaint(bottomSeparatorColor);
    LinePainter2D.paint(g, 0, h - 1, w, h - 1);

    final Color lineColor = JBColor.namedColor("TableHeader.separatorColor", ColorUtil.shift(bg, 0.7));

    config.restore();

    int first = 0;
    int last = model.getColumnCount() - 1;
    if (last >= first) {
      Rectangle clip = g.getClipBounds();
      int columnAtLeft = header.columnAtPoint(new Point(clip.x, clip.y));
      int columnAtRight = header.columnAtPoint(new Point(clip.x + clip.width - 1, clip.y));

      boolean focused = TableUtil.isFocused(header);
      boolean ltr = header.getComponentOrientation().isLeftToRight();

      TableColumn draggedColumn = header.getDraggedColumn();
      if (ltr) {
        if (columnAtLeft == -1) columnAtLeft = first;
        if (columnAtRight == -1) columnAtRight = last;
        Rectangle bounds = header.getHeaderRect(columnAtLeft);
        bounds.height--; // because of bottomSeparatorColor above
        for (int index = columnAtLeft; columnAtRight >= index; index++) {
          if (!skipDrawingVerticalCellSeparator && index != first) paintLine(g, bounds, lineColor);
          paintCell(g, bounds, model, index, focused, draggedColumn);
          bounds.x += bounds.width;
        }
      }
      else {
        if (columnAtRight == -1) columnAtRight = first;
        if (columnAtLeft == -1) columnAtLeft = last;
        Rectangle bounds = header.getHeaderRect(columnAtLeft);
        bounds.height--; // because of bottomSeparatorColor above
        for (int index = columnAtLeft; columnAtRight <= index; index--) {
          if (!skipDrawingVerticalCellSeparator && index != last) paintLine(g, bounds, lineColor);
          paintCell(g, bounds, model, index, focused, draggedColumn);
          bounds.x += bounds.width;
        }
      }

      if (draggedColumn != null) {
        int index = TableUtil.getColumnIndex(header, draggedColumn);

        Rectangle bounds = header.getHeaderRect(index);
        g.setColor(header.getParent().getBackground());
        g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);

        bounds.x += header.getDraggedDistance();
        g.setColor(header.getBackground());
        g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
        paintCell(g, bounds, draggedColumn, index, focused);
      }
      // remove all renderers
      rendererPane.removeAll();
    }
  }

  @Override
  public Dimension getPreferredSize(JComponent c) {
    Dimension size = super.getPreferredSize(c);
    if (size.height == 0) return size;
    JBValue.UIInteger height = new JBValue.UIInteger("TableHeader.height", 25);
    return new Dimension(size.width, Math.max(height.get(), size.height));
  }


  private static void paintLine(Graphics g, Rectangle bounds, Color color) {
    g.setColor(color);
    g.fillRect(bounds.x, bounds.y + 1, 1, bounds.height - 2);
  }

  private void paintCell(Graphics g, Rectangle bounds, TableColumnModel model, int index, boolean focused, TableColumn draggedColumn) {
    TableColumn column = model.getColumn(index);
    bounds.width = column.getWidth();
    if (column != draggedColumn) {
      paintCell(g, bounds, column, index, focused);
    }
  }

  private void paintCell(Graphics g, Rectangle bounds, TableColumn column, int index, boolean focused) {
    Component component = TableUtil.getRendererComponent(header, column, index, focused);
    if (component != null && isExpandableHintShown(column)) {
      Graphics cg = g.create(bounds.x, bounds.y, bounds.width, bounds.height);
      try {
        int width = Math.max(component.getPreferredSize().width, bounds.width);
        rendererPane.paintComponent(cg, component, header, 0, 0, width, bounds.height, true);
      }
      finally {
        cg.dispose();
      }
    }
    else {
      rendererPane.paintComponent(g, component, header, bounds.x, bounds.y, bounds.width, bounds.height, true);
    }
  }

  private boolean isExpandableHintShown(TableColumn column) {
    if (column != null && header instanceof ComponentWithExpandableItems<?> c) {
      return column == ContainerUtil.getFirstItem(c.getExpandableItemsHandler().getExpandedItems());
    }
    return false;
  }
}
