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

import com.intellij.util.ui.ColumnInfo;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;

/**
 * author: lesya
 */
public class ListTreeTableModel extends DefaultTreeModel implements TreeTableModel{

  private final ColumnInfo[] myColumns;

  public ListTreeTableModel(TreeNode root, ColumnInfo[] columns) {
    super(root);
    myColumns = columns;
  }

  public int getColumnCount() {
    return myColumns.length;
  }

  public String getColumnName(int column) {
    return myColumns[column].getName();
  }

  public Object getValueAt(Object node, int column) {
    return myColumns[column].valueOf(node);
  }

  public int getChildCount(Object parent) {
    return ((TreeNode)parent).getChildCount();
  }

  public Object getChild(Object parent, int index) {
    return ((TreeNode)parent).getChildAt(index);
  }

  public Class getColumnClass(int column) {
    return myColumns[column].getColumnClass();
  }

  public boolean isCellEditable(Object node, int column) {
    return myColumns[column].isCellEditable(node);
  }

  public void setValueAt(Object aValue, Object node, int column) {
    myColumns[column].setValue(node, aValue);
  }

  @Override
  public void setTree(JTree tree) {
  }
}
