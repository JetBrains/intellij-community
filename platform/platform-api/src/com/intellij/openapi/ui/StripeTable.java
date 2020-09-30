// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.ui;

import com.intellij.ui.Gray;
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;
import java.awt.*;

/**
 * @author spleaner
 * @author Konstantin Bulenkov
 */
public class StripeTable extends JBTable {
  private static final Color GRID_COLOR = Gray._217;

  public StripeTable(TableModel model) {
    super(model);
    apply(this);
  }

  public static void apply(@NotNull JBTable table) {
    table.setAutoResizeMode(AUTO_RESIZE_OFF);
    table.setTableHeader(createTableHeader(table.getColumnModel()));
    table.getTableHeader().setReorderingAllowed(false);
    //setOpaque(false);
    table.setGridColor(GRID_COLOR);
    table.setIntercellSpacing(new Dimension(1, 0));
    table.setShowGrid(false);
    table.setStriped(true);
  }

  private static JTableHeader createTableHeader(@NotNull TableColumnModel columnModel) {
    return new StripeTableHeader(columnModel);
  }

  private static final class StripeTableHeader extends JTableHeader {
    private final CellRendererPane myRenderPane = new CellRendererPane(); // don't make this static to avoid classloader leaks (IDEA-239761)

    private StripeTableHeader(@NotNull TableColumnModel columnModel) {
      super(columnModel);
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      JViewport viewport = (JViewport)table.getParent();
      if (viewport != null && table.getWidth() < viewport.getWidth()) {
        int x = table.getWidth();
        int width = viewport.getWidth() - table.getWidth();
        paintHeader(g, getTable(), x, width);
      }
    }

    private void paintHeader(Graphics g, JTable table, int x, int width) {
      TableCellRenderer renderer = table.getTableHeader().getDefaultRenderer();
      Component component = renderer.getTableCellRendererComponent(table, "", false, false, -1, 2);
      component.setBounds(0, 0, width, table.getTableHeader().getHeight());
      ((JComponent)component).setOpaque(false);
      myRenderPane.paintComponent(g, component, null, x, 0, width, table.getTableHeader().getHeight(), true);
    }
  }
}
