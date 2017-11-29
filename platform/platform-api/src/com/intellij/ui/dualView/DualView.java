/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.table.BaseTableView;
import com.intellij.ui.table.JBTable;
import com.intellij.ui.table.SelectionProvider;
import com.intellij.ui.table.TableView;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.ui.treeStructure.treetable.ListTreeTableModelOnColumns;
import com.intellij.ui.treeStructure.treetable.TreeTableModel;
import com.intellij.util.config.Storage;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;

public class DualView extends JPanel {
  private static final String TREE = "TREE";
  private static final String FLAT = "FLAT";

  private final CardLayout myCardLayout;

  private TreeTableView myTreeView;

  private JBTable myCurrentView;
  private TableView myFlatView;
  private boolean myRootVisible;
  private CellWrapper myCellWrapper;

  private final Storage.PropertiesComponentStorage myFlatStorage;
  private final Storage.PropertiesComponentStorage myTreeStorage;
  private final PropertyChangeListener myPropertyChangeListener;

  private boolean myZipByHeight;
  private boolean mySuppressStore;

  public DualView(Object root, DualViewColumnInfo[] columns, @NonNls String columnServiceKey, Project project) {
    super(new CardLayout());

    myTreeStorage = new Storage.PropertiesComponentStorage(columnServiceKey + "_tree",
                                                           PropertiesComponent.getInstance(project));
    myFlatStorage = new Storage.PropertiesComponentStorage(columnServiceKey + "_flat",
                                                           PropertiesComponent.getInstance(project));

    myCardLayout = (CardLayout)getLayout();

    add(createTreeComponent(columns, (TreeNode)root), TREE);

    add(createFlatComponent(columns), FLAT);

    myTreeView.getTreeViewModel().addTreeModelListener(new TreeModelListener() {
      public void treeNodesInserted(TreeModelEvent e) {
        refreshFlatModel();
      }

      public void treeNodesRemoved(TreeModelEvent e) {
        refreshFlatModel();
      }

      public void treeStructureChanged(TreeModelEvent e) {
        refreshFlatModel();
      }

      public void treeNodesChanged(TreeModelEvent e) {
        refreshFlatModel();
      }
    });

    setRootVisible(true);

    switchToTheFlatMode();

    restoreState();

    myPropertyChangeListener = new PropertyChangeListener() {
      public void propertyChange(PropertyChangeEvent evt) {
        if (mySuppressStore) return;
        saveState();
      }
    };

    addWidthListenersTo(myTreeView);
    addWidthListenersTo(myFlatView);
  }

  private void addWidthListenersTo(JTable treeView) {
    TableColumnModel columnModel = treeView.getColumnModel();
    int columnCount = columnModel.getColumnCount();
    for (int i = 0; i < columnCount; i++) {
      columnModel.getColumn(i).addPropertyChangeListener(myPropertyChangeListener);
    }
  }

  public void restoreState() {
    BaseTableView.restore(myFlatStorage, myFlatView);
    BaseTableView.restore(myTreeStorage, myTreeView);
  }

  private void refreshFlatModel() {
    ((ListTableModel)myFlatView.getModel()).setItems(myTreeView.getFlattenItems());
  }

  private static ColumnInfo[] createTreeColumns(DualViewColumnInfo[] columns) {
    Collection<ColumnInfo> result = new ArrayList<>();

    final ColumnInfo firstColumn = columns[0];
    ColumnInfo firstTreeColumn = new ColumnInfo(firstColumn.getName()) {
      public Object valueOf(Object object) {
        return firstColumn.valueOf(object);
      }

      public Class getColumnClass() {
        return TreeTableModel.class;
      }

      public boolean isCellEditable(Object o) {
        return true;
      }
    };
    result.add(firstTreeColumn);
    for (int i = 1; i < columns.length; i++) {
      DualViewColumnInfo column = columns[i];
      if (column.shouldBeShownIsTheTree()) result.add(column);
    }

    return result.toArray(new ColumnInfo[result.size()]);
  }

  public void switchToTheFlatMode() {
    if (myFlatView == myCurrentView) return;
    copySelection(myTreeView, myFlatView);
    changeViewTo(myFlatView);
    myCardLayout.show(this, FLAT);
  }

  private void changeViewTo(JBTable view) {
    myCurrentView = view;
    if (myCurrentView != null) {
      myCurrentView.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
      if (myCurrentView instanceof JBTable) {
        myCurrentView.setStriped(true);
      }
      final int row = myCurrentView.getSelectedRow();
      myCurrentView.scrollRectToVisible(myCurrentView.getCellRect(row, 0, true));
    }
  }

  private static void copySelection(SelectionProvider from, SelectionProvider to) {
    to.clearSelection();
    for (Object aSelection : from.getSelection()) {
      to.addSelection(aSelection);
    }
  }

  public void switchToTheTreeMode() {
    if (myTreeView == myCurrentView) return;
    copySelection(myFlatView, myTreeView);
    changeViewTo(myTreeView);
    myCardLayout.show(this, TREE);
  }

  private Component createTreeComponent(DualViewColumnInfo[] columns, TreeNode root) {
    myTreeView = new TreeTableView(new ListTreeTableModelOnColumns(root, createTreeColumns(columns))) {
      public TableCellRenderer getCellRenderer(int row, int column) {
        return createWrappedRenderer(super.getCellRenderer(row, column));
      }

      @Override
      public void doLayout() {
        try {
          mySuppressStore = true;
          super.doLayout();
        }
        finally {
          mySuppressStore = false;
        }
      }
    };
    myTreeView.getTree().getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
    JPanel result = new JPanel(new BorderLayout());
    result.add(ScrollPaneFactory.createScrollPane(myTreeView), BorderLayout.CENTER);
    return result;
  }

  private Component createFlatComponent(DualViewColumnInfo[] columns) {

    ArrayList<ColumnInfo> shownColumns = new ArrayList<>();

    for (DualViewColumnInfo column : columns) {
      if (column.shouldBeShownIsTheTable()) {
        shownColumns.add(column);
      }
    }

    ListTableModel flatModel = new ListTableModel(shownColumns.toArray(new ColumnInfo[shownColumns.size()]));
    //noinspection unchecked
    myFlatView = new TableView(flatModel) {
      public TableCellRenderer getCellRenderer(int row, int column) {
        return createWrappedRenderer(super.getCellRenderer(row, column));
      }

      @NotNull
      @Override
      public Component prepareRenderer(@NotNull TableCellRenderer renderer, int row, int column) {
        final Component c = super.prepareRenderer(renderer, row, column);
        if (c instanceof JComponent && !myFlatView.getCellSelectionEnabled()) {
          ((JComponent)c).setBorder(null);
        }
        return c;
      }

      @Override
      public void doLayout() {
        try {
          mySuppressStore = true;
          super.doLayout();
        }
        finally {
          mySuppressStore = false;
        }
      }

      @Override
      public void updateColumnSizes() {
        // suppress automatic layout, use stored values instead
      }
    };
    myFlatView.setCellSelectionEnabled(false);
    myFlatView.setColumnSelectionAllowed(false);
    myFlatView.setRowSelectionAllowed(true);

    refreshFlatModel();

    JPanel result = new JPanel(new BorderLayout());
    result.add(ScrollPaneFactory.createScrollPane(myFlatView), BorderLayout.CENTER);
    return result;
  }

  private TableCellRenderer createWrappedRenderer(final TableCellRenderer renderer) {
    if (myCellWrapper == null) {
      return renderer;
    }
    else {
      return new MyTableCellRendererWrapper(renderer);
    }
  }

  public void expandAll() {
    expandPath(myTreeView.getTree(), new TreePath(myTreeView.getTree().getModel().getRoot()));
  }

  public void collapseAll() {
    collapsePath(myTreeView.getTree(), new TreePath(myTreeView.getTree().getModel().getRoot()));
  }

  private static void expandPath(JTree tree, TreePath path) {
    tree.expandPath(path);

    final TreeNode node = ((TreeNode)path.getLastPathComponent());
    final Enumeration children = node.children();
    while (children.hasMoreElements()) {
      TreeNode child = (TreeNode)children.nextElement();
      expandPath(tree, path.pathByAddingChild(child));
    }
  }

  private void collapsePath(JTree tree, TreePath path) {

    final TreeNode node = ((TreeNode)path.getLastPathComponent());
    final Enumeration children = node.children();
    while (children.hasMoreElements()) {
      TreeNode child = (TreeNode)children.nextElement();
      collapsePath(tree, path.pathByAddingChild(child));
    }

    if (!((path.getLastPathComponent() == tree.getModel().getRoot()) && !myRootVisible)) {
      tree.collapsePath(path);
    }
  }

  public List getSelection() {
    List<Object> result = ContainerUtil.newArrayList();
    SelectionProvider visibleTable = (SelectionProvider)getVisibleTable();
    for (Object aSelection : visibleTable.getSelection()) {
      result.add(aSelection);
    }
    return result;
  }

  private JTable getVisibleTable() {
    return myCurrentView;
  }

  public void setShowGrid(boolean aBoolean) {
    myTreeView.setShowGrid(aBoolean);
  }

  public void setSelectionInterval(int first, int last) {
    final int treeRowCount = myTreeView.getModel().getRowCount();
    if (first < 0 || last < 0) return;

    if (first < treeRowCount && last < treeRowCount) {
      myTreeView.getSelectionModel().addSelectionInterval(first, last);
    }

    final int flatRowCount = myFlatView.getRowCount();
    if (first < flatRowCount && last < flatRowCount) {
      myFlatView.getSelectionModel().addSelectionInterval(first, last);
    }
  }

  public void addListSelectionListener(ListSelectionListener listSelectionListener) {
    myTreeView.getSelectionModel().addListSelectionListener(listSelectionListener);
    myFlatView.getSelectionModel().addListSelectionListener(listSelectionListener);
  }

  public Tree getTree() {
    return myTreeView.getTree();
  }

  public TreeTableView getTreeView() {
    return myTreeView;
  }

  public TableView getFlatView() {
    return myFlatView;
  }

  public void setViewBorder(Border border) {
    if (myTreeView != null) ((JComponent)myTreeView.getParent().getParent()).setBorder(border);
    if (myFlatView != null) ((JComponent)myFlatView.getParent().getParent()).setBorder(border);
  }

  public void setRootVisible(boolean aBoolean) {
    myRootVisible = aBoolean;
    myTreeView.setRootVisible(myRootVisible);
  }

  public void setTreeCellRenderer(TreeCellRenderer cellRenderer) {
    myTreeView.setTreeCellRenderer(cellRenderer);
  }

  public void setCellWrapper(CellWrapper wrapper) {
    myCellWrapper = wrapper;
  }

  public void installDoubleClickHandler(AnAction action) {
    action.registerCustomShortcutSet(CommonShortcuts.DOUBLE_CLICK_1, myFlatView);
    action.registerCustomShortcutSet(CommonShortcuts.DOUBLE_CLICK_1, myTreeView);
  }

  public void dispose() {
    saveState();
  }

  public void saveState() {
    BaseTableView.store(myFlatStorage, myFlatView);
    BaseTableView.store(myTreeStorage, myTreeView);
  }

  public void setRoot(final TreeNode node, final List<Object> selection) {
    final List<?> currentlySelected = myFlatView.getSelectedObjects();
    final List<?> targetSelection = !currentlySelected.isEmpty() ? currentlySelected : selection;

    myTreeView.getTreeViewModel().setRoot(node);

    if ((targetSelection != null) && (!targetSelection.isEmpty())) {
      final List items = myFlatView.getItems();
      for (Object selElement : targetSelection) {
        if (items.contains(selElement)) {
          final int idx = items.indexOf(selElement);
          setSelectionInterval(idx, idx);
        }
      }
    }
  }

  public void rebuild() {
    ((AbstractTableModel)myFlatView.getModel()).fireTableDataChanged();
    ((AbstractTableModel)myTreeView.getModel()).fireTableDataChanged();
  }

  private class MyTableCellRendererWrapper implements TableCellRendererWrapper {
    @NotNull private final TableCellRenderer myRenderer;

    public MyTableCellRendererWrapper(@NotNull TableCellRenderer renderer) {
      myRenderer = renderer;
    }

    @NotNull
    @Override
    public TableCellRenderer getBaseRenderer() {
      return myRenderer;
    }

    public Component getTableCellRendererComponent(JTable table,
                                                   Object value,
                                                   boolean isSelected,
                                                   boolean hasFocus,
                                                   int row,
                                                   int column) {
      Component result = myRenderer.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      Object treeNode = null;

      final int modelRow = table.convertRowIndexToModel(row);

      if (myCurrentView == myTreeView) {
        TreePath path = myTreeView.getTree().getPathForRow(modelRow);
        if (path != null) {
          treeNode = path.getLastPathComponent();
        }
      }
      else if (myCurrentView == myFlatView) {
        treeNode = myFlatView.getItems().get(modelRow);
      }

      myCellWrapper.wrap(result, table, value, isSelected, hasFocus, row, column, treeNode);
      return result;
    }
  }

  @Override
  public Dimension getPreferredSize() {
    final Dimension was = super.getPreferredSize();
    if (!myZipByHeight) return was;
    final int tableHeight = myFlatView.getTableHeader().getHeight() + myFlatView.getTableViewModel().getRowCount() *
                                                                      myFlatView.getRowHeight();
    return new Dimension(was.width, tableHeight);
  }

  @Override
  public Dimension getMinimumSize() {
    return myZipByHeight ? getPreferredSize() : super.getMinimumSize();
  }

  public void setZipByHeight(boolean zipByHeight) {
    myZipByHeight = zipByHeight;
  }

  public void setEmptyText(@NotNull String text) {
    myTreeView.getEmptyText().setText(text);
    myFlatView.getEmptyText().setText(text);
  }
}
