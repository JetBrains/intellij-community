// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.treeStructure.treetable;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.treeStructure.TreeBulkExpansionEvent;
import com.intellij.ui.treeStructure.TreeBulkExpansionListener;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.tree.TreePath;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This is a wrapper class takes a TreeTableModel and implements
 * the table model interface. The implementation is trivial, with
 * all the event dispatching support provided by the superclass:
 * the AbstractTableModel.
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

    tree.addTreeExpansionListener(new TreeBulkExpansionListener() {
      // don't use fireTableRowsInserted() here; the selection model would get updated twice.
      @Override
      public void treeExpanded(TreeExpansionEvent event) {
        if (!isBulkOperationInProgress(event)) {
          fireTableDataChanged();
        }
      }

      @Override
      public void treeCollapsed(TreeExpansionEvent event) {
        if (!isBulkOperationInProgress(event)) {
          fireTableDataChanged();
        }
      }

      private static boolean isBulkOperationInProgress(TreeExpansionEvent event) {
        return event instanceof TreeBulkExpansionEvent bulkEvent && bulkEvent.isBulkOperationInProgress();
      }

      @Override
      public void treeBulkExpansionEnded(@NotNull TreeBulkExpansionEvent event) {
        fireTableDataChanged();
      }

      @Override
      public void treeBulkCollapseEnded(@NotNull TreeBulkExpansionEvent event) {
        fireTableDataChanged();
      }
    });

    // Install a TreeModelListener that can update the table when a tree changes.
    // We use delayedFireTableDataChanged as we cannot be guaranteed the tree will have finished processing the event before us.
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
  public Class<?> getColumnClass(int column) {
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
   * processed.
   */
  protected void delayedFireTableDataChanged() {
    long stamp = modificationStamp.incrementAndGet();
    ApplicationManager.getApplication().invokeLater(() -> {
      if (stamp == modificationStamp.get()) {
        fireTableDataChanged();
      }
    });
  }

  @Override
  public void fireTableDataChanged() {
    // have to restore table selection since AbstractDataModel.fireTableDataChanged() clears all selection
    TreePath[] treePaths = tree.getSelectionPaths();
    super.fireTableDataChanged();
    if (treePaths != null) {
      for (TreePath treePath : treePaths) {
        final int row = tree.getRowForPath(treePath);
        table.getSelectionModel().addSelectionInterval(row, row);
      }
    }
  }
}
