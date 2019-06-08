// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.treeStructure.treetable;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.TableUtil;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.accessibility.ScreenReader;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.plaf.UIResource;
import javax.swing.tree.DefaultTreeSelectionModel;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;
import java.util.*;

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
  private static final Logger LOG = Logger.getInstance(TreeTable.class);
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
    new TreeAction("selectPreviousColumn", "selectParent");
    new TreeAction("selectPreviousColumnExtendSelection", "selectParentExtendSelection");
    new TreeAction("selectNextColumn", "selectChild");
    new TreeAction("selectNextColumnExtendSelection", "selectChildExtendSelection");
  }

  private final class TreeAction extends AbstractAction implements UIResource {
    private final String actionId;
    private final Action action;

    private TreeAction(@NotNull String tableActionId, @NotNull String treeActionId) {
      super(tableActionId + " -> " + treeActionId);
      actionId = treeActionId;
      ActionMap map = getActionMap();
      action = map.get(tableActionId);
      if (!(action instanceof TreeAction)) {
        map.put(tableActionId, this);
      }
    }

    private JTree getTreeToPerformAction() {
      JTree tree = !myProcessCursorKeys ? null : getTree();
      if (tree == null || 1 != getSelectedRowCount()) return null;
      if (!getColumnModel().getColumnSelectionAllowed()) return tree;
      int column = getColumnModel().getSelectionModel().getAnchorSelectionIndex();
      return 0 <= column && column < getColumnCount() && !isTreeColumn(column) ? null : tree;
    }

    @Override
    public void actionPerformed(ActionEvent event) {
      JTree tree = getTreeToPerformAction();
      Action action = tree == null ? null : tree.getActionMap().get(actionId);
      if (action == null) {
        this.action.actionPerformed(event);
      }
      else {
        action.actionPerformed(new ActionEvent(tree, ActionEvent.ACTION_PERFORMED, actionId));
        int row = tree.getLeadSelectionRow();
        getSelectionModel().setSelectionInterval(row, row);
        TableUtil.scrollSelectionToVisible(TreeTable.this);
      }
    }
  }

  public void setModel(TreeTableModel treeTableModel) {// Create the tree. It will be used as a renderer and editor.
    if (myTree != null) {
      myTree.removePropertyChangeListener(JTree.ROW_HEIGHT_PROPERTY, myTreeRowHeightPropertyListener);
    }
    myTree = new TreeTableTree(treeTableModel, this);
    setRowHeight(myTree.getRowHeight());
    myTreeRowHeightPropertyListener = new PropertyChangeListener() {
      @Override
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
      setRowHeight(JBUIScale.scale(18));  // Metal looks better like this.
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
  @Override
  public void updateUI() {
    super.updateUI();
    if (myTree!= null) {
      myTree.updateUI();
    }
    // Use the tree's default foreground and background colors in the
    // table.
    LookAndFeel.installColorsAndFont(this, "Tree.background", "Tree.foreground", "Tree.font");
  }

  /* Workaround for BasicTableUI anomaly. Make sure the UI never tries to
   * paint the editor. The UI currently uses different techniques to
   * paint the renderers and editors and overriding setBounds() below
   * is not the right thing to do for an editor. Returning -1 for the
   * editing row in this case, ensures the editor is never painted.
   */
  @Override
  public int getEditingRow() {
    return editingColumn == -1 || isTreeColumn(editingColumn) ? -1 : editingRow;
  }

  /**
   * Overridden to pass the new rowHeight to the tree.
   */
  @Override
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

    ListToTreeSelectionModelWrapper() {
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
     * This is overriden to set {@code updatingListSelectionModel}
     * and message super. This is the only place DefaultTreeSelectionModel
     * alters the ListSelectionModel.
     */
    @Override
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
     * If {@code updatingListSelectionModel} is false, this will
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
              addSelectionPaths(selectionPaths.toArray(new TreePath[0]));
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
      @Override
      public void valueChanged(ListSelectionEvent e) {
        updateSelectedPathsFromSelectedRows();
      }
    }
  }

  @Override
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

