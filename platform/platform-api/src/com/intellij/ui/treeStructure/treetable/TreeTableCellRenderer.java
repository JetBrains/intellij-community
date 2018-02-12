/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.ui.components.panels.OpaquePanel;
import com.intellij.util.ui.ClientPropertyHolder;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.accessibility.AccessibleContextDelegate;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleState;
import javax.accessibility.AccessibleStateSet;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.table.TableCellRenderer;
import javax.swing.tree.TreeCellRenderer;
import java.awt.*;

/**
 * A TreeCellRenderer that displays a JTree.
 */
public class TreeTableCellRenderer implements TableCellRenderer, ClientPropertyHolder {
  private final TreeTable myTreeTable;
  private final TreeTableTree myTree;
  private TreeCellRenderer myTreeCellRenderer;
  private final TableCellRendererComponent myCellRendererComponent = new TableCellRendererComponent();
  private Border myDefaultBorder = UIUtil.getTableFocusCellHighlightBorder();


  public TreeTableCellRenderer(TreeTable treeTable, TreeTableTree tree) {
    myTreeTable = treeTable;
    myTree = tree;
  }

  public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
    int modelRow  = table.convertRowIndexToModel(row);
    final boolean lineHasFocus = table.hasFocus();

    if (myTreeCellRenderer != null)
      myTree.setCellRenderer(myTreeCellRenderer);
    if (isSelected){
      myTree.setBackground(lineHasFocus ? table.getSelectionBackground() : UIUtil.getTreeUnfocusedSelectionBackground());
      myTree.setForeground(table.getSelectionForeground());
    }
    else {
      myTree.setBackground(table.getBackground());
      myTree.setForeground(table.getForeground());
    }

    myCellRendererComponent.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());

    //TableModel model = myTreeTable.getModel();
    //myTree.setTreeTableTreeBorder(hasFocus && model.getColumnClass(column).equals(TreeTableModel.class) ? myDefaultBorder : null);
    myTree.setVisibleRow(modelRow);

    final Object treeObject = myTree.getPathForRow(modelRow).getLastPathComponent();
    boolean leaf = myTree.getModel().isLeaf(treeObject);
    final boolean expanded = myTree.isExpanded(modelRow);
    Component component = myTree.getCellRenderer().getTreeCellRendererComponent(myTree, treeObject, isSelected, expanded, leaf, modelRow, lineHasFocus);
    if (component instanceof JComponent) {
      table.setToolTipText(((JComponent)component).getToolTipText());
    }

    //myTree.setCellFocused(false);

    myCellRendererComponent.setComponent(component, expanded, leaf);
    return myCellRendererComponent;
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

  /**
   * This component has two purposes:
   * <ul>
   * <li>from a UI perspective, it is a {@link JPanel} that contains a single {@link myTree} element,
   * so that it renders the active row in the table tree, including indentation, icons, etc. when painted.</li>
   * <li>from an accessibility perspective, it exposes the accessibility context of the {@link myComponent} it wraps, so
   * that screen readers see the accessible components corresponding to each tree node in the tree.</li>
   * </ul>
   * See the {@link TreeTableCellRenderer#getTableCellRendererComponent(JTable, Object, boolean, boolean, int, int)} method:
   * <ul>
   * <li>returning {@link myTree} would allow for the painting behavior of cells to be correct, but would be incorrect from an
   * accessibility point of view, as each cell would be exposed as a "tree" component.</li>
   * <li>returning {@link myComponent} would be correct from an accessibility point of view, as each cell would expose
   * the tree node they contain, but would not work from a rendering perspective, because we would miss the
   * indentation markers, styles, etc. that is handled by {@link TreeTableTree} when rendering tree nodes.</li>
   * </ul>
   */
  private class TableCellRendererComponent extends OpaquePanel {
    /** The component resulting from rendering a cell of the TreeTableTree column */
    private Component myComponent;
    private boolean myExpanded;
    private boolean myLeaf;

    public TableCellRendererComponent() {
      super(new BorderLayout());
    }

    public void setComponent(Component component, boolean expanded, boolean leaf) {
      myComponent = component;
      myExpanded = expanded;
      myLeaf = leaf;

      // Since we wrap a new component, we need to reset our accessible context
      accessibleContext = null;

      // By adding the tree as our only child, we ensure the row corresponding
      // to the cell will be painted inside our bounds.
      if (getComponentCount() == 0) {
        add(myTree, BorderLayout.CENTER);
      }
    }

    @Override
    public AccessibleContext getAccessibleContext() {
      // Return the accessible context of the component we wrap (if it is accessible)
      if (accessibleContext == null) {
        if ((myComponent instanceof Accessible) && (myComponent.getAccessibleContext() != null)) {
          accessibleContext = new AccessibleTableCellRendererComponent(myComponent.getAccessibleContext());
        } else {
          // If myComponent is not accessible -- which should be rare for a fully accessible application,
          // returning the default JPanel accessibility context is a reasonable default.
          accessibleContext = super.getAccessibleContext();
        }
      }
      return accessibleContext;
    }

    protected class AccessibleTableCellRendererComponent extends AccessibleContextDelegate {

      public AccessibleTableCellRendererComponent(AccessibleContext context) {
        super(context);
      }

      @Override
      public AccessibleStateSet getAccessibleStateSet() {
        AccessibleStateSet set = super.getAccessibleStateSet();
        if (!myLeaf) {
          // Add expandable+expanded/collapsed states so that screen readers announce
          // that this is an item that can be expanded (or collapsed).
          set.add(AccessibleState.EXPANDABLE);
          set.add(myExpanded ? AccessibleState.EXPANDED : AccessibleState.COLLAPSED);
        }
        return set;
      }
    }
  }
}
