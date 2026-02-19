// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.codeStyle.arrangement.match;

import com.intellij.application.options.codeStyle.arrangement.ArrangementConstants;
import com.intellij.application.options.codeStyle.arrangement.ui.ArrangementEditorAware;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import javax.swing.table.DefaultTableModel;

public final class ArrangementMatchingRulesModel extends DefaultTableModel {

  private static final Logger LOG = Logger.getInstance(ArrangementMatchingRulesModel.class);

  @Override
  public int getColumnCount() {
    return 1;
  }

  public Object getElementAt(int row) {
    return getValueAt(row, 0);
  }
  
  public void set(int row, Object value) {
    setValueAt(value, row, 0);
  }

  public void insert(int row, Object value) {
    insertRow(row, new Object[] { value });
  }
  
  @Override
  public void setValueAt(Object aValue, int row, int column) {
    if (ArrangementConstants.LOG_RULE_MODIFICATION) {
      LOG.info(String.format("Setting match rule '%s' to index %d", aValue, row));
    }
    super.setValueAt(aValue, row, column);
  }
  
  public void add(@NotNull Object data) {
    addRow(new Object[] { data });
  }
  
  @Override
  public void addRow(Object[] rowData) {
    if (ArrangementConstants.LOG_RULE_MODIFICATION) {
      LOG.info(String.format("Adding match rule '%s' (to index %d)", rowData[0], getRowCount()));
    }
    super.addRow(rowData);
  }

  @Override
  public void removeRow(int row) {
    if (ArrangementConstants.LOG_RULE_MODIFICATION) {
      LOG.info(String.format("Removing match rule '%s' from index %d", getValueAt(row, 0), row));
    }
    super.removeRow(row);
  }

  public void clear() {
    getDataVector().removeAllElements();
  }
  
  public int getSize() {
    return getRowCount();
  }

  @Override
  public boolean isCellEditable(int row, int column) {
    return getValueAt(row, column) instanceof ArrangementEditorAware;
  }
}
