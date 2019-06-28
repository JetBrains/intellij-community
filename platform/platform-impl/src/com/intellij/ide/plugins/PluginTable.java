// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.TableUtil;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.TextTransferable;
import com.intellij.util.ui.UIUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.datatransfer.Transferable;

public class PluginTable extends JBTable {
  public PluginTable(final PluginTableModel model) {
    super(model);
    getColumnModel().setColumnMargin(0);
    for (int i = 0; i < model.getColumnCount(); i++) {
      TableColumn column = getColumnModel().getColumn(i);
      final ColumnInfo columnInfo = model.getColumnInfos()[i];
      column.setCellEditor(columnInfo.getEditor(null));
      if (columnInfo.getColumnClass() == Boolean.class) {
        TableUtil.setupCheckboxColumn(column, JBUIScale.scale(16));
      }
    }

    setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    setShowGrid(false);
    this.setTableHeader(null);
    setTransferHandler(new TransferHandler() {
      @Nullable
      @Override
      protected Transferable createTransferable(JComponent c) {
        final IdeaPluginDescriptor[] selectedValues = getSelectedObjects();
        if (selectedValues == null) return null;
        final String text = StringUtil.join(selectedValues, descriptor -> descriptor.getName(), ", ");
        final String htmlText = "<body>\n<ul>\n" + StringUtil.join(selectedValues, descriptor -> descriptor.getName(), "</li>\n<li>") + "</ul>\n</body>\n";
        return new TextTransferable(XmlStringUtil.wrapInHtml(htmlText), text);
      }

      @Override
      public int getSourceActions(JComponent c) {
        return COPY;
      }
    });
  }

  @Override
  public void paint(@NotNull Graphics g) {
    super.paint(g);
    UIUtil.fixOSXEditorBackground(this);
  }

  public void setColumnWidth(final int columnIndex, final int width) {
    TableColumn column = getColumnModel().getColumn(columnIndex);
    column.setMinWidth(width);
    column.setMaxWidth(width);
  }

  @Override
  protected boolean isSortOnUpdates() {
    return false;
  }

  @Override
  public void setValueAt(final Object aValue, final int row, final int column) {
    super.setValueAt(aValue, row, column);
    repaint(); //in order to update invalid plugins
  }

  @Override
  public TableCellRenderer getCellRenderer(final int row, final int column) {
    final ColumnInfo columnInfo = ((PluginTableModel)getModel()).getColumnInfos()[column];
    return columnInfo.getRenderer(getObjectAt(row));
  }

  public Object[] getElements() {
    return ((PluginTableModel)getModel()).view.toArray();
  }

  public IdeaPluginDescriptor getObjectAt(int row) {
    return ((PluginTableModel)getModel()).getObjectAt(convertRowIndexToModel(row));
  }

  public void select(IdeaPluginDescriptor... descriptors) {
    PluginTableModel tableModel = (PluginTableModel)getModel();
    getSelectionModel().clearSelection();
    for (int i=0; i<tableModel.getRowCount();i++) {
      IdeaPluginDescriptor descriptorAt = tableModel.getObjectAt(i);
      if (ArrayUtil.find(descriptors,descriptorAt) != -1) {
        final int row = convertRowIndexToView(i);
        getSelectionModel().addSelectionInterval(row, row);
      }
    }
    TableUtil.scrollSelectionToVisible(this);
  }

  public IdeaPluginDescriptor getSelectedObject() {
    IdeaPluginDescriptor selected = null;
    if (getSelectedRowCount() > 0) {
      selected = getObjectAt(getSelectedRow());
    }
    return selected;
  }

  public IdeaPluginDescriptor[] getSelectedObjects() {
    IdeaPluginDescriptor[] selection = null;
    if (getSelectedRowCount() > 0) {
      int[] poses = getSelectedRows();
      selection = new IdeaPluginDescriptor[poses.length];
      for (int i = 0; i < poses.length; i++) {
        selection[i] = getObjectAt(poses[i]);
      }
    }
    return selection;
  }
}
