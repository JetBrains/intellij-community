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

import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.ui.Table;

import javax.swing.*;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * @author spleaner
 */
public class StripeTable extends Table {
  private static final Color EVEN_ROW_COLOR = new Color(241, 245, 250);
  private static final Color GRID_COLOR = new Color(217, 217, 217);
  private static final CellRendererPane RENDER_PANE = new CellRendererPane();

  public StripeTable(TableModel model) {
    super(model);

    setAutoResizeMode(AUTO_RESIZE_OFF);
    setTableHeader(createTableHeader());
    getTableHeader().setReorderingAllowed(false);
    setOpaque(false);
    setGridColor(GRID_COLOR);
    setIntercellSpacing(new Dimension(1, 0));
    setShowGrid(false);
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

  @Override
  public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
    Component component = super.prepareRenderer(renderer, row, column);
    if (component instanceof JComponent) {
      ((JComponent)component).setOpaque(getSelectionModel().isSelectedIndex(row)  || row % 2 == 0 );
      if (!getSelectionModel().isSelectedIndex(row) && row % 2 == 0) component.setBackground(EVEN_ROW_COLOR);
    }

    return component;
  }

  public static JScrollPane createScrollPane(JTable table) {
    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(table);
    scrollPane.setViewport(new StripedViewport(table));
    scrollPane.getViewport().setView(table);
    scrollPane.setBorder(UIManager.getBorder("Table.scrollPaneBorder"));
    return scrollPane;
  }

  private static class StripedViewport extends JViewport {
    private final JTable myTable;

    public StripedViewport(JTable table) {
      myTable = table;
      setOpaque(false);
      initListeners();
    }

    private void initListeners() {
      PropertyChangeListener listener = createTableColumnWidthListener();
      for (int i = 0; i < myTable.getColumnModel().getColumnCount(); i++) {
        myTable.getColumnModel().getColumn(i).addPropertyChangeListener(listener);
      }
    }

    private PropertyChangeListener createTableColumnWidthListener() {
      return new PropertyChangeListener() {
        public void propertyChange(PropertyChangeEvent evt) {
          repaint();
        }
      };
    }

    @Override
    protected void paintComponent(Graphics g) {
      int x = 0;
      for (int i = 0; i < myTable.getColumnCount(); i++) {
        TableColumn column = myTable.getColumnModel().getColumn(i);
        x += column.getWidth();
        g.setColor(GRID_COLOR);
        g.drawLine(x - 1, g.getClipBounds().y, x - 1, getHeight());
      }

      super.paintComponent(g);
    }
  }

}
