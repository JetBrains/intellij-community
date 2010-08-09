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
package com.intellij.ui.table;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.config.Storage;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import com.intellij.util.ui.SortableColumnModel;
import com.intellij.util.ui.Table;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Do NOT add code wich assumes that table has same number of rows as model. It isn't true!
 */
public abstract class BaseTableView extends Table {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ui.table.BaseTableView");

  public BaseTableView() {
    this(new ListTableModel(ColumnInfo.EMPTY_ARRAY));
  }

  public BaseTableView(final ListTableModel model) {
    super(model);
    final JTableHeader tableHeader = getTableHeader();
    if (tableHeader != null) {
      tableHeader.addMouseListener(new MouseAdapter() {
        public void mouseClicked(final MouseEvent e) {
          final int column = convertColumnIndexToModel(tableHeader.columnAtPoint(e.getPoint()));
          if (column > -1) {
            onHeaderClicked(column);
          }
        }
      });
    }
  }

  protected abstract void onHeaderClicked(int column);

  protected ListTableModel getListTableModel() {
    return (ListTableModel) getModel();
  }

  public void setModel(final TableModel dataModel) {
    LOG.assertTrue(dataModel instanceof SortableColumnModel);
    super.setModel(dataModel);
    dataModel.addTableModelListener(new TableModelListener() {
      public void tableChanged(final TableModelEvent e) {
        JTableHeader header = getTableHeader();
        if (header != null) {
          header.repaint();
        }
      }
    });
  }

  @NonNls
  private static String orderPropertyName(final int index) {
    return "Order"+index;
  }

  @NonNls
  private static String widthPropertyName(final int index) {
    return "Width" + index;
  }

  public static void store(final Storage storage, final JTable table) {
    final TableColumnModel model = table.getTableHeader().getColumnModel();
    final int columnCount = model.getColumnCount();
    final boolean[] storedColumns = new boolean[columnCount];
    Arrays.fill(storedColumns, false);
    for (int i = 0; i < columnCount; i++) {
      final TableColumn column = model.getColumn(i);
      storage.put(widthPropertyName(i), String.valueOf(column.getWidth()));
      final int modelIndex = column.getModelIndex();
      storage.put(orderPropertyName(i), String.valueOf(modelIndex));
      if (storedColumns[modelIndex]) {
        LOG.error("columnCount: " + columnCount + " current: " + i + " modelINdex: " + modelIndex);
      }
      storedColumns[modelIndex] = true;
    }
  }

  public static void storeWidth(final Storage storage, final TableColumnModel columns) {
    for (int i = 0; i < columns.getColumnCount(); i++) {
      storage.put(widthPropertyName(i), String.valueOf(columns.getColumn(i).getWidth()));
    }
  }

  public static void restore(final Storage storage, final JTable table) {
    final TableColumnModel columnModel = table.getTableHeader().getColumnModel();
    int index = 0;
    final ArrayList<String> columnIndices = new ArrayList<String>();
    while (true) {
      final String order = storage.get(orderPropertyName(index));
      if (order == null) break;
      columnIndices.add(order);
      index++;
      if (index == table.getColumnCount()) break;
    }
    index = 0;
    for (final String columnIndex : columnIndices) {
      columnModel.moveColumn(indexbyModelIndex(columnModel, Integer.parseInt(columnIndex)), index);
      index++;
    }
    for (int i = 0; i < columnIndices.size(); i++) {
      final String width = storage.get(widthPropertyName(i));
      if (width != null && width.length() > 0) {
        try {
          columnModel.getColumn(i).setPreferredWidth(Integer.parseInt(width));
        } catch(NumberFormatException e) {
          LOG.error("Bad width: " + width + " at column: "+ i + " from: " + storage +
                    " actual columns count: " + columnModel.getColumnCount() +
                    " info count: " + columnIndices.size(), e);
        }
      }
    }
  }

  public static void restoreWidth(final Storage storage, final TableColumnModel columns) {
    for (int index = 0; true; index++) {
      final String widthValue = storage.get(widthPropertyName(index));
      if (widthValue == null) break;
      try {
        columns.getColumn(index).setPreferredWidth(Integer.parseInt(widthValue));
      } catch(NumberFormatException e) {
        LOG.error("Bad width: " + widthValue + " at column: " + index + " from: " + storage, e);
      }
    }
  }

  private static int indexbyModelIndex(final TableColumnModel model, final int index) {
    for (int i = 0; i < model.getColumnCount(); i++)
      if (model.getColumn(i).getModelIndex() == index)
        return i;
    LOG.error("Total: " + model.getColumnCount() + " index: " + index);
    return index;
  }
}
