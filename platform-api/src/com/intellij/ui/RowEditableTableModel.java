package com.intellij.ui;

import javax.swing.table.TableModel;

/**
 * @author dsl
 */
public interface RowEditableTableModel extends TableModel {
  void addRow();

  void removeRow(int index);

  void exchangeRows(int index1, int index2);
}
