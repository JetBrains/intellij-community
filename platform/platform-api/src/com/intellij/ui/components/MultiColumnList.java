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
package com.intellij.ui.components;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.KeyEvent;

/**
 * @author Konstantin Bulenkov
 */
public class MultiColumnList extends JTable {
  private final ListModel myModel;
  private ListCellRenderer myRenderer;
  private JList myList;
  private Dimension myPrefSize;

  public MultiColumnList(ListModel model) {
    super(new FixedRowsModel(model, 25));
    myModel = model;
    setRowHeight(20);
    setShowGrid(false);
    setCellSelectionEnabled(true);
    setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    //noinspection UndesirableClassUsage
    myList = new JList(model) {
      @Override
      public void setBorder(Border border) {
        super.setBorder(border);
        MultiColumnList.this.setBorder(border);
      }

      @Override
      public void repaint() {
        MultiColumnList.this.repaint();
      }

      @Override
      public void setCellRenderer(ListCellRenderer cellRenderer) {
        super.setCellRenderer(cellRenderer);
        if (myRenderer != cellRenderer) {
          MultiColumnList.this.setCellRenderer(cellRenderer);
        }
      }
    };
    getColumnModel().setColumnMargin(0);
  }

  @Override
  protected void processKeyEvent(KeyEvent e) {
    final int key = e.getKeyCode();
    final int col = getSelectedColumn();
    final int row = getSelectedRow();
    final MultiColumnListModel model = getModel();
    int r = row;
    int c = col;
    if (key == KeyEvent.VK_RIGHT) {
      if (c + 1 < getColumnCount()) {
        c++;
      }
    } else if (key == KeyEvent.VK_DOWN) {
      if (r + 1 < getRowCount()) {
        r++;
        if (model.toListIndex(r, c ) >= model.getSize()) {
          r = 0; c++;
        }
      } else {
        r = 0; c++;
      }
    }
    if (col != c || row != r) {
      if (e.getID() == KeyEvent.KEY_RELEASED) return;
      int index = model.toListIndex(r, c);
      if (index >= model.getSize() || r >= getRowCount() || c >= getColumnCount()) {
        e.consume();
        final int last = model.getSize() - 1;
        changeSelection(model.getRow(last), model.getColumn(last), false, false);
      } else {
        changeSelection(r, c, false, false);
        e.consume();
      }
    } else {
      super.processKeyEvent(e);
    }
  }

  public MultiColumnList(Object...elements) {
    this(createListModel(elements));
  }

  private static ListModel createListModel(Object...elements) {
    final DefaultListModel model = new DefaultListModel();
    for (Object element : elements) {
      model.addElement(element);
    }

    return model;
  }

  public void setCellRenderer(ListCellRenderer renderer) {
    myRenderer = renderer;
    setDefaultRenderer(Object.class, new TableCellRenderer() {
      @Override
      public Component getTableCellRendererComponent(JTable table,
                                                     Object value,
                                                     boolean isSelected,
                                                     boolean hasFocus,
                                                     int row,
                                                     int column) {
        final int index = getModel().toListIndex(row, column);
        if (isSelected) {
          myList.addSelectionInterval(index, index);
        } else {
          myList.removeSelectionInterval(index, index);
        }
        return myRenderer.getListCellRendererComponent(myList, value, index, isSelected, hasFocus);
      }
    });
  }

  public void setFixedRowsMode(int maxRows) {
    if (maxRows < 1) {
      throw new IllegalArgumentException("Should be greater than 0");
    }

    setModel(new FixedRowsModel(myModel, maxRows));
    getModel().fireTableStructureChanged();
  }

  @Override
  public MultiColumnListModel getModel() {
    return (MultiColumnListModel)super.getModel();
  }

  public void setFixedColumnsMode(int maxColumns) {
    if (maxColumns < 1) {
      throw new IllegalArgumentException("Should be greater than 0");
    }

    setModel(new FixedColumnsModel(myModel, maxColumns));
    getModel().fireTableStructureChanged();
  }

  public JList getDelegate() {
    return myList;
  }

  @Override
  public Dimension getPreferredSize() {
    if (myPrefSize == null) {
      Dimension dimension = new Dimension();
      int rowHeight = 0;
      for (int column = 0; column < getColumnCount(); column++) {
        int columnWidth = 0;
        for (int row = 0; row < getRowCount(); row++) {
          final TableCellRenderer renderer = getCellRenderer(row, column);
          if (renderer != null) {
            final Object value = getValueAt(row, column);
            final Component component = renderer.getTableCellRendererComponent(this, value, true, true, row, column);
            if (component != null) {
              final Dimension size = component.getPreferredSize();
              rowHeight = Math.max(size.height, rowHeight);
              columnWidth = Math.max(size.width, columnWidth);
            }
          }
        }
        getColumnModel().getColumn(column).setWidth(columnWidth + 5);
        dimension.width += columnWidth + 5;
      }
      dimension.height = getRowCount() * rowHeight;
      myPrefSize = dimension;
    }
    return myPrefSize;
  }

  public static void main(String[] args) {
    final JFrame frame = new JFrame("Test");
    frame.setSize(300, 300);
    frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

    final MultiColumnList list = new MultiColumnList("1", 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13);
    list.setFixedColumnsMode(5);
    frame.getContentPane().add(list);
    frame.setVisible(true);
  }
}
