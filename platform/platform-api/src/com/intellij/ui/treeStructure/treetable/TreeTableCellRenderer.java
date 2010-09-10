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

import com.intellij.util.ui.ClientPropertyHolder;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableModel;
import javax.swing.tree.TreeCellRenderer;
import java.awt.*;

/**
 * A TreeCellRenderer that displays a JTree.
 */
public class TreeTableCellRenderer implements TableCellRenderer, ClientPropertyHolder {
  private final TreeTable myTreeTable;
  private final TreeTableTree myTree;
  private TreeCellRenderer myTreeCellRenderer;
  private Border myDefaultBorder = UIUtil.getTableFocusCellHighlightBorder();


  public TreeTableCellRenderer(TreeTable treeTable, TreeTableTree tree) {
    myTreeTable = treeTable;
    myTree = tree;
  }

  public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
    int modelRow  = table.convertRowIndexToModel(row);

    if (myTreeCellRenderer != null)
      myTree.setCellRenderer(myTreeCellRenderer);
    if (isSelected){
      myTree.setBackground(table.getSelectionBackground());
      myTree.setForeground(table.getSelectionForeground());
    }
    else{
      myTree.setBackground(table.getBackground());
      myTree.setForeground(table.getForeground());
    }

    TableModel model = myTreeTable.getModel();
    myTree.setTreeTableTreeBorder(hasFocus && model.getColumnClass(column).equals(TreeTableModel.class) ? myDefaultBorder : null);
    myTree.setVisibleRow(modelRow);

    final Object treeObject = myTree.getPathForRow(modelRow).getLastPathComponent();
    boolean leaf = myTree.getModel().isLeaf(treeObject);
    final boolean expanded = myTree.isExpanded(modelRow);
    Component component = myTree.getCellRenderer().getTreeCellRendererComponent(myTree, treeObject, isSelected, expanded, leaf, modelRow, hasFocus);
    if (component instanceof JComponent) {
      table.setToolTipText(((JComponent)component).getToolTipText());
    }

    myTree.setCellFocused(hasFocus);

    return myTree;
  }

  public void setCellRenderer(TreeCellRenderer treeCellRenderer) {
    myTreeCellRenderer = treeCellRenderer;
  }
  public void setDefaultBorder(Border border) {
    myDefaultBorder = border;
  }

  public void putClientProperty(String key, Object value) {
    myTree.putClientProperty(key, value);
  }

  public void putClientProperty(String s, String s1) {
    putClientProperty(s, (Object)s1);
  }

  public void setRootVisible(boolean b) {
    myTree.setRootVisible(b);
  }

  public void setShowsRootHandles(boolean b) {
    myTree.setShowsRootHandles(b);
  }

}
