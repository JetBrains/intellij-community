// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.treeStructure.treetable;

import com.intellij.openapi.util.NlsContexts;

import javax.swing.*;
import javax.swing.tree.TreeModel;

/**
 * TreeTableModel is the model used by a JTreeTable. It extends TreeModel
 * to add methods for getting inforamtion about the set of columns each
 * node in the TreeTableModel may have. Each column, like a column in
 * a TableModel, has a name and a type associated with it. Each node in
 * the TreeTableModel can return a value for each of the columns and
 * set that value if isCellEditable() returns true.
 *
 * @author Philip Milne
 * @author Scott Violet
 */
public interface TreeTableModel extends TreeModel {
  /**
   * Returns the number ofs availible column.
   */
  int getColumnCount();

  /**
   * Returns the name for column number {@code column}.
   */
  @NlsContexts.ColumnName String getColumnName(int column);

  /**
   * Returns the type for column number {@code column}.
   */
  Class getColumnClass(int column);

  /**
   * Returns the value to be displayed for node {@code node},
   * at column number {@code column}.
   */
  Object getValueAt(Object node, int column);

  /**
   * Indicates whether the value for node {@code node},
   * at column number {@code column} is editable.
   */
  boolean isCellEditable(Object node, int column);

  /**
   * Sets the value for node {@code node},
   * at column number {@code column}.
   */
  void setValueAt(Object aValue, Object node, int column);

  void setTree(JTree tree);
}
