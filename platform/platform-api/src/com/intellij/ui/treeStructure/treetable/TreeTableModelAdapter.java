/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.tree.TreePath;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This is a wrapper class takes a TreeTableModel and implements
 * the table model interface. The implementation is trivial, with
 * all of the event dispatching support provided by the superclass:
 * the AbstractTableModel.
 *
 * @version 1.2 10/27/98
 *
 * @author Philip Milne
 * @author Scott Violet
 */
public class TreeTableModelAdapter extends AbstractTableModel {
  private final AtomicInteger modificationStamp = new AtomicInteger();

  private final JTree tree;
  private final TreeTableModel treeTableModel;
  private final JTable table;

  public TreeTableModelAdapter(TreeTableModel treeTableModel, JTree tree, JTable table) {
    this.tree = tree;
    this.treeTableModel = treeTableModel;
    this.table = table;
    this.treeTableModel.setTree(tree);

    tree.addTreeExpansionListener(new TreeExpansionListener() {
      // Don't use fireTableRowsInserted() here; the selection model
      // would get updated twice.
      @Override
      public void treeExpanded(TreeExpansionEvent event) {
        fireTableDataChanged();
      }

      @Override
      public void treeCollapsed(TreeExpansionEvent event) {
        fireTableDataChanged();
      }
    });

    // Install a TreeModelListener that can update the table when
    // tree changes. We use delayedFireTableDataChanged as we can
    // not be guaranteed the tree will have finished processing
    // the event before us.
    treeTableModel.addTreeModelListener(new TreeModelListener() {
      @Override
      public void treeNodesChanged(TreeModelEvent e) {
        delayedFireTableDataChanged();
      }

      @Override
      public void treeNodesInserted(TreeModelEvent e) {
        delayedFireTableDataChanged();
      }

      @Override
      public void treeNodesRemoved(TreeModelEvent e) {
        delayedFireTableDataChanged();
      }

      @Override
      public void treeStructureChanged(TreeModelEvent e) {
        delayedFireTableDataChanged();
      }
    });

  }

  // Wrappers, implementing TableModel interface.

  @Override
  public int getColumnCount() {
    return treeTableModel.getColumnCount();
  }

  @Override
  public String getColumnName(int column) {
    return treeTableModel.getColumnName(column);
  }

  @Override
  public Class getColumnClass(int column) {
    return treeTableModel.getColumnClass(column);
  }

  @Override
  public int getRowCount() {
    return tree.getRowCount();
  }

  protected Object nodeForRow(int row) {
    TreePath treePath = tree.getPathForRow(row);
    return treePath == null ? null : treePath.getLastPathComponent();
  }

  @Override
  public Object getValueAt(int row, int column) {
    final Object o = nodeForRow(row);
    return o == null? null : treeTableModel.getValueAt(o, column);
  }

  @Override
  public boolean isCellEditable(int row, int column) {
    final Object o = nodeForRow(row);
    return o != null && treeTableModel.isCellEditable(o, column);
  }

  @Override
  public void setValueAt(Object value, int row, int column) {
    final Object o = nodeForRow(row);
    if (o != null) treeTableModel.setValueAt(value, o, column);
  }

  /**
   * Invokes fireTableDataChanged after all the pending events have been
   * processed. SwingUtilities.invokeLater is used to handle this.
   */
  protected void delayedFireTableDataChanged() {
    long stamp = modificationStamp.incrementAndGet();
    ApplicationManager.getApplication().invokeLater(() -> {
      if (stamp != modificationStamp.get()) return;
      fireTableDataChanged();
    }, ModalityState.any());
  }

  @Override
  public void fireTableDataChanged() {
    // have to restore table selection since AbstractDataModel.fireTableDataChanged() clears all selection
    final TreePath[] treePaths = tree.getSelectionPaths();
    super.fireTableDataChanged();
    if (treePaths != null) {
      for (TreePath treePath : treePaths) {
        final int row = tree.getRowForPath(treePath);
        table.getSelectionModel().addSelectionInterval(row, row);
      }
    }
  }

}
