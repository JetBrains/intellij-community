/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.openapi.ui;

import com.intellij.ui.Gray;
import com.intellij.ui.table.JBTable;

import javax.swing.*;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableModel;
import java.awt.*;

/**
 * @author spleaner
 * @author Konstantin Bulenkov
 */
public class StripeTable extends JBTable {
  private static final Color GRID_COLOR = Gray._217;
  private static final CellRendererPane RENDER_PANE = new CellRendererPane();

  public StripeTable(TableModel model) {
    super(model);

    setAutoResizeMode(AUTO_RESIZE_OFF);
    setTableHeader(createTableHeader());
    getTableHeader().setReorderingAllowed(false);
    //setOpaque(false);
    setGridColor(GRID_COLOR);
    setIntercellSpacing(new Dimension(1, 0));
    setShowGrid(false);
    setStriped(true);
  }

  private JTableHeader createTableHeader() {
    return new JTableHeader(getColumnModel()) {
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
    };
  }

  private static void paintHeader(Graphics g, JTable table, int x, int width) {
    TableCellRenderer renderer = table.getTableHeader().getDefaultRenderer();
    Component component = renderer.getTableCellRendererComponent(table, "", false, false, -1, 2);
    component.setBounds(0, 0, width, table.getTableHeader().getHeight());
    ((JComponent)component).setOpaque(false);
    RENDER_PANE.paintComponent(g, component, null, x, 0, width, table.getTableHeader().getHeight(), true);
  }
}
