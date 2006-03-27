/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.ui.dualView;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.peer.PeerFactory;
import com.intellij.ui.table.ItemsProvider;
import com.intellij.ui.table.SelectionProvider;
import com.intellij.ui.table.TableHeaderRenderer;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.SortableColumnModel;
import com.intellij.util.ui.treetable.*;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public class TreeTableView extends TreeTable implements ItemsProvider, SelectionProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ui.dualView.TreeTableView");
  public TreeTableView(ListTreeTableModelOnColumns treeTableModel) {
    super(treeTableModel);
    setRootVisible(false);

    getTableHeader().setDefaultRenderer(new TableHeaderRenderer(treeTableModel));

    setTreeCellRenderer(new TreeCellRenderer() {
      private final TreeCellRenderer myBaseRenderer = PeerFactory.getInstance().getUIHelper().createHighlightableTreeCellRenderer();
      public Component getTreeCellRendererComponent(JTree tree1,
                                                    Object value,
                                                    boolean selected,
                                                    boolean expanded,
                                                    boolean leaf,
                                                    int row,
                                                    boolean hasFocus) {
        JComponent result = (JComponent)myBaseRenderer.getTreeCellRendererComponent(tree1, value, selected, expanded, leaf, row, hasFocus);
        result.setOpaque(!selected);
        return result;
      }
    });

    setSizes();
  }

  public void setTableModel(TreeTableModel treeTableModel) {
    super.setTableModel(treeTableModel);
    LOG.assertTrue(treeTableModel instanceof SortableColumnModel);
    getTableHeader().setDefaultRenderer(new TableHeaderRenderer((SortableColumnModel)treeTableModel));
  }

  private void setSizes() {
    ColumnInfo[] columns = ((ListTreeTableModelOnColumns)getTableModel()).getColumns();
    for (int i = 0; i < columns.length; i++) {
      ColumnInfo columnInfo = columns[i];
      TableColumn column = getColumnModel().getColumn(i);
      if (columnInfo.getWidth(this) > 0) {
        int width = columnInfo.getWidth(this);
        column.setMaxWidth(width);
        column.setMinWidth(width);
      }
    }
  }

  public TableCellEditor getCellEditor(int row, int column) {
    ColumnInfo columnInfo = getTreeViewModel().getColumnInfos()[column];
    TableCellEditor editor = getEditor(columnInfo, getTree().getPathForRow(row).getLastPathComponent());
    if (editor == null) {
      return super.getCellEditor(row, column);
    }
    else {
      return editor;
    }
  }

  protected TableCellEditor getEditor(final ColumnInfo columnInfo, final Object o) {
    return columnInfo.getEditor(o);
  }

  public TreeTableCellRenderer createTableRenderer(TreeTableModel treeTableModel) {
    return new TreeTableCellRenderer(TreeTableView.this, (TreeTableTree)getTree()) {
      public Component getTableCellRendererComponent(JTable table,
                                                     Object value,
                                                     boolean isSelected,
                                                     boolean hasFocus,
                                                     int row,
                                                     int column) {
        JComponent component = (JComponent)super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row,
                                                                               column);
        if (isSelected) {
          component.setBackground(table.getSelectionBackground());
          component.setForeground(table.getSelectionForeground());
        }
        else {
          component.setBackground(table.getBackground());
          component.setForeground(table.getForeground());
        }
        component.setOpaque(isSelected);
        return getTree();
      }
    };
  }


  ListTreeTableModelOnColumns getTreeViewModel() {
    return (ListTreeTableModelOnColumns)getTableModel();
  }

  public List getFlattenItems() {
    List items = getTreeViewModel().getItems();
    ArrayList result = new ArrayList();
    for (Iterator each = items.iterator(); each.hasNext();) {
      DualTreeElement object = (DualTreeElement)each.next();
      if (object.shouldBeInTheFlatView()) result.add(object);
    }

    return result;
  }

  protected boolean isRenderWithDefaultColors() {
    return true;
  }

  public TableCellRenderer getCellRenderer(int row, int column) {
    ColumnInfo columnInfo = getTreeViewModel().getColumnInfos()[convertColumnIndexToModel(column)];
    TableCellRenderer renderer = getRenderer(columnInfo, getTree().getPathForRow(row).getLastPathComponent());
    final TableCellRenderer baseRenderer = renderer == null ? super.getCellRenderer(row, column) : renderer;
    return isRenderWithDefaultColors() ? new TableCellRenderer() {
      public Component getTableCellRendererComponent(JTable table,
                                                     Object value,
                                                     boolean isSelected,
                                                     boolean hasFocus,
                                                     int row,
                                                     int column) {
        final JComponent rendererComponent = (JComponent)baseRenderer.getTableCellRendererComponent(
          table, value, isSelected, hasFocus, row, column);
        if (isSelected) {
          rendererComponent.setBackground(table.getSelectionBackground());
          rendererComponent.setForeground(table.getSelectionForeground());
        }
        else {
          rendererComponent.setBackground(table.getBackground());
          rendererComponent.setForeground(table.getForeground());
        }
        rendererComponent.setOpaque(isSelected);
        return rendererComponent;
      }
    } : baseRenderer;
  }

  protected TableCellRenderer getRenderer(final ColumnInfo columnInfo, final Object o) {
    return columnInfo.getRenderer(o);
  }

  public List getItems() {
    return getTreeViewModel().getItems();
  }

  public Collection getSelection() {
    ArrayList result = new ArrayList();
    TreePath[] selectionPaths = getTree().getSelectionPaths();
    if (selectionPaths == null) return result;
    for (int i = 0; i < selectionPaths.length; i++) {
      result.add(selectionPaths[i].getLastPathComponent());
    }
    return result;
  }

  public void addSelection(Object item) {
    getTree().setExpandsSelectedPaths(true);
    DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode)item;
    addSelectedPath(new TreePath(treeNode.getPath()));
  }

}
