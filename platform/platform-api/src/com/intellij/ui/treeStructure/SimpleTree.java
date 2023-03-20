// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.treeStructure;

import com.intellij.ide.util.treeView.NodeRenderer;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.TreeUIHelper;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.CellEditorListener;
import javax.swing.event.ChangeEvent;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;

public class SimpleTree extends Tree implements CellEditorListener {
  private static final SimpleNode NULL_NODE = new NullNode();
  private static final int INVALID = -1;

  private ActionGroup myPopupGroup;
  private String myPlace;

  private JComponent myEditorComponent;
  private int myEditingRow;
  private boolean myIgnoreSelectionChange;

  private int myMinHeightInRows = 5;

  public SimpleTree() {
    setModel(new DefaultTreeModel(new PatchedDefaultMutableTreeNode()));
    TreeUtil.installActions(this);

    configureUiHelper(TreeUIHelper.getInstance());

    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(@NotNull MouseEvent e) {
        handleDoubleClickOrEnter(getClosestPathForLocation(e.getX(), e.getY()), e);
        return false;
      }
    }.installOn(this);
    addMouseListener(new MyMouseListener());
    setCellRenderer(new NodeRenderer());

    setEditable(false);
    getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
    ToolTipManager.sharedInstance().registerComponent(this);

    getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
      @Override
      public void valueChanged(TreeSelectionEvent e) {
        if (!myIgnoreSelectionChange && hasSingleSelection()) {
          getNodeFor(getSelectionPath()).handleSelection(SimpleTree.this);
        }
      }
    });

    addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER && hasSingleSelection()) {
          handleDoubleClickOrEnter(getSelectionPath(), e);
        }
        if (e.getKeyCode() == KeyEvent.VK_F2 && e.getModifiers() == 0) {
          e.consume(); // ignore start editing by F2
        }
      }
    });

    setOpaque(false);
  }

  public SimpleTree(TreeModel aModel) {
    this();
    setModel(aModel);
  }

  protected void configureUiHelper(final TreeUIHelper helper) {
    helper.installTreeSpeedSearch(this);
  }

  public void setPopupGroup(ActionGroup aPopupGroup, String aPlace) {
    myPopupGroup = aPopupGroup;
    myPlace = aPlace;
  }

  public SimpleNode getNodeFor(int row) {
    return getNodeFor(getPathForRow(row));
  }

  public SimpleNode getNodeFor(TreePath aPath) {
    SimpleNode node = TreeUtil.getLastUserObject(SimpleNode.class, aPath);
    return node != null ? node : NULL_NODE;
  }

  @Nullable
  public TreePath getPathFor(SimpleNode node) {
    final TreeNode nodeWithObject = TreeUtil.findNodeWithObject((DefaultMutableTreeNode)getModel().getRoot(), node);
    if (nodeWithObject != null) {
      return TreeUtil.getPathFromRoot(nodeWithObject);
    }
    return null;
  }

  @Nullable
  public SimpleNode getSelectedNode() {
    if (isSelectionEmpty()) {
      return null;
    }

    return getNodeFor(getSelectionPath());
  }

  @Override
  public boolean isSelectionEmpty() {
    final TreePath selection = super.getSelectionPath();
    return selection == null || getNodeFor(selection) == NULL_NODE;

  }

  public SimpleNode[] getSelectedNodesIfUniform() {
    List<SimpleNode> result = new ArrayList<>();
    TreePath[] selectionPaths = getSelectionPaths();
    if (selectionPaths != null) {
      SimpleNode lastNode = null;
      for (TreePath selectionPath : selectionPaths) {
        SimpleNode nodeFor = getNodeFor(selectionPath);

        if (lastNode != null && lastNode.getClass() != nodeFor.getClass()) {
          return new SimpleNode[0];
        }

        result.add(nodeFor);
        lastNode = nodeFor;
      }
    }
    return result.toArray(new SimpleNode[0]);
  }

  @Override
  protected void paintChildren(Graphics g) {
    super.paintChildren(g);
    g.setColor(UIManager.getColor("Tree.line"));

    for (int row = 0; row < getRowCount(); row++) {
      final TreePath path = getPathForRow(row);
      if (!getNodeFor(path).shouldHaveSeparator()) {
        continue;
      }

      final Rectangle bounds = getRowBounds(row);
      int x = (int)bounds.getMaxX();
      int y = (int)(bounds.getY() + bounds.height / 2);
      g.drawLine(x, y, getWidth() - 5, y);
    }
  }

  public void doClick(int row) {
    setSelectionRow(row);
  }

  // From FTree:

  @Override
  public void cancelEditing() {
    if (isEditing()) {
      cellEditor.cancelCellEditing();
      doStopEditing();
    }
  }

  @Override
  public void editingStopped(ChangeEvent e) {
    doStopEditing();
  }

  @Override
  public void editingCanceled(ChangeEvent e) {
    doStopEditing();
  }

  public JComponent getEditorComponent() {
    return myEditorComponent;
  }

  @Override
  public boolean isEditing() {
    return myEditorComponent != null;
  }

  @Override
  public TreePath getEditingPath() {
    if (isEditing()) {
      return getPathForRow(myEditingRow);
    }
    return super.getEditingPath();
  }

  @Override
  public boolean isPathEditable(TreePath path) {
    return true;
  }

  @Override
  protected boolean paintNodes() {
    return true;
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);

    if (isEditing()) {
      Rectangle editedNodeRect = getRowBounds(myEditingRow);
      if (editedNodeRect == null) return;
      g.setColor(getBackground());
      g.fillRect(editedNodeRect.x, editedNodeRect.y, editedNodeRect.width, editedNodeRect.height);
    }
  }

  @Override
  public void setCellEditor(TreeCellEditor aCellEditor) {
    if (cellEditor != null) {
      cellEditor.removeCellEditorListener(this);
    }
    super.setCellEditor(aCellEditor);

    if (cellEditor != null) {
      cellEditor.addCellEditorListener(this);
    }
  }

  @Override
  public boolean stopEditing() {
    boolean result = isEditing();
    if (result) {
      if (!cellEditor.stopCellEditing()) {
        cellEditor.cancelCellEditing();
      }
      doStopEditing();
    }
    return result;
  }

  @Override
  public void startEditingAtPath(final TreePath path) {
    if (path != null && isVisible(path)) {

      if (isEditing() && !stopEditing()) {
        return;
      }

      startEditing(path);
    }
  }

  private void startEditing(final TreePath path) {

    CellEditor editor = getCellEditor();
    if (editor != null && editor.isCellEditable(null) && isPathEditable(path)) {

      getSelectionModel().clearSelection();
      getSelectionModel().setSelectionPath(path);

      myEditingRow = getRowForPath(path);
      myEditorComponent = (JComponent)getCellEditor()
        .getTreeCellEditorComponent(this, path.getLastPathComponent(), isPathSelected(path), isExpanded(path),
                                    treeModel.isLeaf(path.getLastPathComponent()), myEditingRow);

      putEditor(path);

      if (myEditorComponent.isFocusable()) {
        myEditorComponent.requestFocusInWindow();
      }

      SwingUtilities.invokeLater(() -> scrollPathToVisible(path));
    }
  }

  private void putEditor(TreePath path) {

    add(myEditorComponent);
    Rectangle nodeBounds = getPathBounds(path);
    Dimension editorPrefSize = myEditorComponent.getPreferredSize();
    if (editorPrefSize.height > nodeBounds.height) {
      nodeBounds.y -= (editorPrefSize.height - nodeBounds.height) / 2;
      nodeBounds.height = editorPrefSize.height;
    }

    myEditorComponent.setBounds(nodeBounds);
  }

  private void doStopEditing() {
    if (isEditing()) {
      remove(myEditorComponent);
      myEditorComponent = null;
      setSelectionRow(myEditingRow);
      myEditingRow = INVALID;
      repaint();
    }
  }

  @Override
  public void addSelectionPath(TreePath path) {
    myIgnoreSelectionChange = true;
    super.addSelectionPath(path);
    myIgnoreSelectionChange = false;
  }

  @Override
  public void addSelectionPaths(TreePath[] path) {
    myIgnoreSelectionChange = true;
    super.addSelectionPaths(path);
    myIgnoreSelectionChange = false;
  }

  private boolean isSelected(TreePath path) {
    TreePath[] selectionPaths = getSelectionPaths();
    return selectionPaths != null && ArrayUtil.contains(path, selectionPaths);
  }

  public boolean isMultipleSelection() {
    return getSelectionRows() != null && getSelectionRows().length > 1;
  }

  private void handleDoubleClickOrEnter(final TreePath treePath, final InputEvent e) {
    Runnable runnable = () -> getNodeFor(treePath).handleDoubleClickOrEnter(this, e);
    ApplicationManager.getApplication().invokeLater(runnable, ModalityState.stateForComponent(this));
  }

  protected void invokeContextMenu(final MouseEvent e) {
    SwingUtilities.invokeLater(() -> JBPopupMenu.showByEvent(e, myPlace, myPopupGroup));
  }

  private class MyMouseListener extends MouseAdapter {
    @Override
    public void mousePressed(MouseEvent e) {
      invokePopup(e);
    }

    @Override
    public void mouseReleased(MouseEvent e) {
      invokePopup(e);
    }

    @Override
    public void mouseClicked(MouseEvent e) {
      invokePopup(e);
    }

    private void invokePopup(final MouseEvent e) {
      if (e.isPopupTrigger() && insideTreeItemsArea(e)) {

        selectPathUnderCursorIfNeeded(e);

        if (myPopupGroup != null) {
          invokeContextMenu(e);
        }
      }
    }

    private void selectPathUnderCursorIfNeeded(final MouseEvent e) {
      TreePath pathForLocation = getClosestPathForLocation(e.getX(), e.getY());
      if (!isSelected(pathForLocation)) {
        setSelectionPath(pathForLocation);
      }
    }

    private boolean insideTreeItemsArea(MouseEvent e) {
      Rectangle rowBounds = getRowBounds(getRowCount() - 1);
      if (rowBounds == null) {
        return false;
      }
      double lastItemBottomLine = rowBounds.getMaxY();
      return e.getY() <= lastItemBottomLine;
    }
  }

  private boolean hasSingleSelection() {
    return !isSelectionEmpty() && getSelectionPaths().length == 1;
  }

  public DefaultTreeModel getBuilderModel() {
    return (DefaultTreeModel)getModel();
  }

  public NodeRenderer getRenderer() {
    return (NodeRenderer)getCellRenderer();
  }

  public String toString() {
    return getClass().getName() + '#' + System.identityHashCode(this);
  }

  public final void setMinSizeInRows(int rows) {
    myMinHeightInRows = rows;
  }

  @Override
  public Dimension getMinimumSize() {
    Dimension superSize = super.getMinimumSize();

    if (myMinHeightInRows == -1) return superSize;
    int rowCount = getRowCount();
    if (rowCount == 0) return superSize;

    double rowHeight = getRowBounds(0).getHeight();
    return new Dimension(superSize.width, (int)(rowHeight * myMinHeightInRows));
  }

  @Override
  public void processKeyEvent(final KeyEvent e) {
    super.processKeyEvent(e);
  }
}
