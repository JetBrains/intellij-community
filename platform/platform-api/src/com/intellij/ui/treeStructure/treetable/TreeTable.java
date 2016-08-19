/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.accessibility.ScreenReader;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.tree.DefaultTreeSelectionModel;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.*;
import java.util.List;

/**
 * This example shows how to create a simple JTreeTable component,
 * by using a JTree as a renderer (and editor) for the cells in a
 * particular column in the JTable.
 *
 * @version 1.2 10/27/98
 *
 * @author Philip Milne
 * @author Scott Violet
 */
public class TreeTable extends JBTable {
  /** A subclass of JTree. */
  private TreeTableTree myTree;
  private TreeTableModel myTableModel;
  private PropertyChangeListener myTreeRowHeightPropertyListener;
  // If a screen reader is present, it is better to let the left/right cursor keys
  // be routed to the JTable, as opposed to expand/collapse tree nodes.
  private boolean myProcessCursorKeys = !ScreenReader.isActive();

  public TreeTable(TreeTableModel treeTableModel) {
    super();
    setModel(treeTableModel);
  }

  @SuppressWarnings({"MethodOverloadsMethodOfSuperclass"})
  public void setModel(TreeTableModel treeTableModel) {// Create the tree. It will be used as a renderer and editor.
    if (myTree != null) {
      myTree.removePropertyChangeListener(JTree.ROW_HEIGHT_PROPERTY, myTreeRowHeightPropertyListener);
    }
    myTree = new TreeTableTree(treeTableModel, this);
    setRowHeight(myTree.getRowHeight());
    myTreeRowHeightPropertyListener = new PropertyChangeListener() {
      public void propertyChange(PropertyChangeEvent evt) {
        int treeRowHeight = myTree.getRowHeight();
        if (treeRowHeight == getRowHeight()) return;
        setRowHeight(treeRowHeight);
      }
    };
    myTree.addPropertyChangeListener(JTree.ROW_HEIGHT_PROPERTY, myTreeRowHeightPropertyListener);

    // Install a tableModel representing the visible rows in the tree.
    setTableModel(treeTableModel);
    // Force the JTable and JTree to share their row selection models.
    ListToTreeSelectionModelWrapper selectionWrapper = new ListToTreeSelectionModelWrapper();
    myTree.setSelectionModel(selectionWrapper);
    setSelectionModel(selectionWrapper.getListSelectionModel());

    // Install the tree editor renderer and editor.
    TreeTableCellRenderer treeTableCellRenderer = createTableRenderer(treeTableModel);
    setDefaultRenderer(TreeTableModel.class, treeTableCellRenderer);
    setDefaultEditor(TreeTableModel.class, new TreeTableCellEditor(treeTableCellRenderer));

    // No grid.
    setShowGrid(false);

    // No intercell spacing
    setIntercellSpacing(new Dimension(0, 0));

    // And update the height of the trees row to match that of the table.
    if (myTree.getRowHeight() < 1) {
      setRowHeight(JBUI.scale(18));  // Metal looks better like this.
    }
    else {
      setRowHeight(getRowHeight());
    }
  }

  public TreeTableModel getTableModel() {
    return myTableModel;
  }

  public void setTableModel(TreeTableModel treeTableModel) {
    myTableModel = treeTableModel;
    super.setModel(adapt(treeTableModel));
  }

  protected TreeTableModelAdapter adapt(TreeTableModel treeTableModel) {
    return new TreeTableModelAdapter(treeTableModel, myTree, this);
  }

  public void setRootVisible(boolean visible){
    myTree.setRootVisible(visible);
  }

  public void putTreeClientProperty(Object key, Object value){
    myTree.putClientProperty(key, value);
  }

  public void setTreeCellRenderer(TreeCellRenderer renderer){
    myTree.setCellRenderer(renderer);
  }

  /**
   * Overridden to message super and forward the method to the tree.
   * Since the tree is not actually in the component hierarchy it will
   * never receive this unless we forward it in this manner.
   */
  public void updateUI() {
    super.updateUI();
    if (myTree!= null) {
      myTree.updateUI();
    }
    // Use the tree's default foreground and background colors in the
    // table.
    //noinspection HardCodedStringLiteral
    LookAndFeel.installColorsAndFont(this, "Tree.background", "Tree.foreground", "Tree.font");
  }

  /* Workaround for BasicTableUI anomaly. Make sure the UI never tries to
   * paint the editor. The UI currently uses different techniques to
   * paint the renderers and editors and overriding setBounds() below
   * is not the right thing to do for an editor. Returning -1 for the
   * editing row in this case, ensures the editor is never painted.
   */
  public int getEditingRow() {
    return editingColumn == -1 || isTreeColumn(editingColumn) ? -1 : editingRow;
  }

  /**
   * Overridden to pass the new rowHeight to the tree.
   */
  public void setRowHeight(int rowHeight) {
    super.setRowHeight(rowHeight);
    if (myTree != null && myTree.getRowHeight() < rowHeight) {
      myTree.setRowHeight(getRowHeight());
    }
  }

  /**
   * @return the tree that is being shared between the model.
   */
  public TreeTableTree getTree() {
    return myTree;
  }

  protected void processKeyEvent(KeyEvent e){
    if (!myProcessCursorKeys) {
      super.processKeyEvent(e);
      return;
    }

    int keyCode = e.getKeyCode();
    final int selColumn = columnModel.getSelectionModel().getAnchorSelectionIndex();
    boolean treeHasFocus = selColumn == -1 || selColumn >= 0 && isTreeColumn(selColumn);
    boolean oneRowSelected = getSelectedRowCount() == 1;
    int rowToSelect = -1;
    if(treeHasFocus && oneRowSelected && ((keyCode == KeyEvent.VK_LEFT) || (keyCode == KeyEvent.VK_RIGHT))){
      TreePath selectionPath = myTree.getSelectionPath();
      myTree._processKeyEvent(e);
      if (myTree.isExpanded(selectionPath)) {
        myTree.setSelectionPath(selectionPath);
      } else if (keyCode == KeyEvent.VK_LEFT) {
        rowToSelect = myTree.getRowForPath(myTree.getSelectionPath());
        if (!getScrollableTracksViewportHeight()) {
          int visibleYPosition = getVisibleRect().y;
          final Rectangle cellRect = getCellRect(rowToSelect, 0, false);
          int selectedRowYPosition = cellRect.y;
          if (selectedRowYPosition < visibleYPosition) {
            scrollRectToVisible(cellRect);
          }
        }
      }
    }
    else{
      super.processKeyEvent(e);
    }
    if (rowToSelect > -1) {
      getSelectionModel().setSelectionInterval(rowToSelect, rowToSelect);
    }
  }

  /**
   * Enable or disable processing of left/right cursor keys to expand/collapse
   * nodes in the tree column. Disabling these keys can be useful to improve
   * accessibility support when the left/right cursor keys are better suited to
   * navigate to the previous/next cell of a given row.
   */
  public void setProcessCursorKeys(boolean processCursorKeys) {
    myProcessCursorKeys = processCursorKeys;
  }

  /**
   * ListToTreeSelectionModelWrapper extends DefaultTreeSelectionModel
   * to listen for changes in the ListSelectionModel it maintains. Once
   * a change in the ListSelectionModel happens, the paths are updated
   * in the DefaultTreeSelectionModel.
   */
  private class ListToTreeSelectionModelWrapper extends DefaultTreeSelectionModel {
    /** Set to true when we are updating the ListSelectionModel. */
    protected boolean updatingListSelectionModel;

    public ListToTreeSelectionModelWrapper() {
      super();
      getListSelectionModel().addListSelectionListener(createListSelectionListener());
    }

    /**
     * @return the list selection model. ListToTreeSelectionModelWrapper
     * listens for changes to this model and updates the selected paths
     * accordingly.
     */
    ListSelectionModel getListSelectionModel() {
      return listSelectionModel;
    }

    /**
     * This is overriden to set <code>updatingListSelectionModel</code>
     * and message super. This is the only place DefaultTreeSelectionModel
     * alters the ListSelectionModel.
     */
    public void resetRowSelection() {
      if (!updatingListSelectionModel) {
        updatingListSelectionModel = true;
        try {
          Set<Integer> selectedRows = new HashSet<>();
          int min = listSelectionModel.getMinSelectionIndex();
          int max = listSelectionModel.getMaxSelectionIndex();

          if (min != -1 && max != -1) {
            for (int counter = min; counter <= max; counter++) {
              if (listSelectionModel.isSelectedIndex(counter)) {
                selectedRows.add(new Integer(counter));
              }
            }
          }

          super.resetRowSelection();

          listSelectionModel.clearSelection();
          for (final Object selectedRow : selectedRows) {
            Integer row = (Integer)selectedRow;
            listSelectionModel.addSelectionInterval(row.intValue(), row.intValue());
          }
        }
        finally {
          updatingListSelectionModel = false;
        }
      }
      // Notice how we don't message super if
      // updatingListSelectionModel is true. If
      // updatingListSelectionModel is true, it implies the
      // ListSelectionModel has already been updated and the
      // paths are the only thing that needs to be updated.
    }

    /**
     * @return a newly created instance of ListSelectionHandler.
     */
    protected ListSelectionListener createListSelectionListener() {
      return new ListSelectionHandler();
    }

    /**
     * If <code>updatingListSelectionModel</code> is false, this will
     * reset the selected paths from the selected rows in the list
     * selection model.
     */
    protected void updateSelectedPathsFromSelectedRows() {
      if (!updatingListSelectionModel) {
        updatingListSelectionModel = true;
        try {
          // This is way expensive, ListSelectionModel needs an
          // enumerator for iterating.
          int min = listSelectionModel.getMinSelectionIndex();
          int max = listSelectionModel.getMaxSelectionIndex();

          clearSelection();
          if (min != -1 && max != -1) {
            List<TreePath> selectionPaths = new ArrayList<>();
            for (int counter = min; counter <= max; counter++) {
              if (listSelectionModel.isSelectedIndex(counter)) {
                TreePath selPath = myTree.getPathForRow(counter);

                if (selPath != null) {
                  selectionPaths.add(selPath);
                }
              }
            }
            if (!selectionPaths.isEmpty()) {
              addSelectionPaths(selectionPaths.toArray(new TreePath[selectionPaths.size()]));
            }
          }
        }
        finally {
          updatingListSelectionModel = false;
        }
      }
    }

    /**
     * Class responsible for calling updateSelectedPathsFromSelectedRows
     * when the selection of the list changse.
     */
    class ListSelectionHandler implements ListSelectionListener {
      public void valueChanged(ListSelectionEvent e) {
        updateSelectedPathsFromSelectedRows();
      }
    }
  }

  public boolean editCellAt(int row, int column, EventObject e) {
    boolean editResult = super.editCellAt(row, column, e);
    if (e instanceof MouseEvent && isTreeColumn(column)){
      MouseEvent me = (MouseEvent)e;
      int y = me.getY();

      if (getRowHeight() != myTree.getRowHeight()) {
        // fix y if row heights are not equal
        // [todo]: review setRowHeight to synchronize heights correctly!
        final Rectangle tableCellRect = getCellRect(row, column, true);
        y = Math.min(y - tableCellRect.y, myTree.getRowHeight() - 1) + row * myTree.getRowHeight();
      }

      MouseEvent newEvent = new MouseEvent(myTree, me.getID(),
        me.getWhen(), me.getModifiers(),
        me.getX() - getCellRect(0, column, true).x,
        y, me.getClickCount(),
        me.isPopupTrigger()
      );
      myTree.dispatchEvent(newEvent);

      // Some LAFs, for example, Aqua under MAC OS X
      // expand tree node by MOUSE_RELEASED event. Unfortunately,
      // it's not possible to find easy way to wedge in table's
      // event sequense. Therefore we send "synthetic" release event.
      if (newEvent.getID()==MouseEvent.MOUSE_PRESSED) {
        MouseEvent newME2 = new MouseEvent(
          myTree,
          MouseEvent.MOUSE_RELEASED,
          me.getWhen(), me.getModifiers(),
          me.getX() - getCellRect(0, column, true).x,
          y - getCellRect(0, column, true).y, me.getClickCount(),
          me.isPopupTrigger()
        );
        myTree.dispatchEvent(newME2);
      }
    }    
    return editResult;
  }

  protected boolean isTreeColumn(int column) {
    return TreeTableModel.class.isAssignableFrom(getColumnClass(column));
  }

  public void addSelectedPath(TreePath path) {
    int row = getTree().getRowForPath(path);
    getTree().addSelectionPath(path);
    getSelectionModel().addSelectionInterval(row, row);
  }

  public void removeSelectedPath(TreePath path) {
    int row = getTree().getRowForPath(path);
    getTree().removeSelectionPath(path);
    getSelectionModel().removeSelectionInterval(row, row);
  }

  public TreeTableCellRenderer createTableRenderer(TreeTableModel treeTableModel) {
    return new TreeTableCellRenderer(this, myTree);
  }

  public void setMinRowHeight(int i) {
    setRowHeight(Math.max(getRowHeight(), i));
  }

}

