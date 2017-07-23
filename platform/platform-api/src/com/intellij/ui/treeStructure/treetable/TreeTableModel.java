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
package com.intellij.ui.treeStructure.treetable;

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
  String getColumnName(int column);

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
