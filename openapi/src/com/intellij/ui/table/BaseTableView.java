/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.ui.table;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.config.Storage;
import com.intellij.util.ui.ListTableModel;
import com.intellij.util.ui.SortableColumnModel;
import com.intellij.util.ui.Table;

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
import java.util.Iterator;

/**
 * Do NOT add code wich assumes that table has same number of rows as model. It isn't true!
 */
public abstract class BaseTableView extends Table {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ui.TableView");

  public BaseTableView(final ListTableModel model) {
    super(model);
    final JTableHeader tableHeader = getTableHeader();
    if (tableHeader != null) {
      tableHeader.addMouseListener(new MouseAdapter() {
        public void mouseClicked(final MouseEvent e) {
          final int column = convertColumnIndexToModel(tableHeader.columnAtPoint(e.getPoint()));
          onHeaderClicked(column);
        }
      });

      model.addTableModelListener(new TableModelListener(){
        public void tableChanged(final TableModelEvent e) {
          JTableHeader header = getTableHeader();
          if (header != null) {
            header.repaint();
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
  }

  private static String orderPropertyName(final int index) {
    return "Order"+index;
  }

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
      LOG.assertTrue(!storedColumns[modelIndex],
                     "columnCount: " + columnCount + " current: " + i + " modelINdex: " + modelIndex);
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
    final ArrayList columnIndicies = new ArrayList();
    while (true) {
      final String order = storage.get(orderPropertyName(index));
      if (order == null) break;
      columnIndicies.add(order);
      index++;
      if (index == table.getColumnCount()) break;
    }
    index = 0;
    for (Iterator iterator = columnIndicies.iterator(); iterator.hasNext();) {
      final String order = (String) iterator.next();
      columnModel.moveColumn(indexbyModelIndex(columnModel, Integer.parseInt(order)), index);
      index++;
    }
    for (int i = 0; i < columnIndicies.size(); i++) {
      final String width = storage.get(widthPropertyName(i));
      try {
        columnModel.getColumn(i).setPreferredWidth(Integer.parseInt(width));
      } catch(NumberFormatException e) {
        LOG.error("Bad width: " + width + " at column: "+ i + " from: " + storage +
                  " actual columns count: " + columnModel.getColumnCount() +
                  " info count: " + columnIndicies.size(), e);
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
    LOG.assertTrue(false, "Total: " + model.getColumnCount() + " index: "+ index);
    return index;
  }
}
