/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.roots.libraries.ui.impl;

import com.intellij.ui.CheckboxTree;
import com.intellij.ui.CheckedTreeNode;
import com.intellij.ui.ClickListener;
import com.intellij.ui.dualView.TreeTableView;
import com.intellij.ui.treeStructure.treetable.ListTreeTableModelOnColumns;
import com.intellij.ui.treeStructure.treetable.TreeTableTree;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.tree.TreeUtil;

import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Enumeration;

/**
 * @author nik
 */
public class CheckboxTreeTable extends TreeTableView {
  public CheckboxTreeTable(CheckedTreeNode root, CheckboxTree.CheckboxTreeCellRenderer renderer, final ColumnInfo[] columns) {
    super(new ListTreeTableModelOnColumns(root, columns));
    initTree(getTree(), renderer);
  }

  //todo[nik] I hate to copy-paste but have to copy the code below from CheckboxTreeBase to support CheckboxTree inside TreeTable in IDEA 11.1.x branch
  //todo[nik] I solemnly swear to get rid of this code in IDEA 12 branch
  private void initTree(final TreeTableTree tree, final CheckboxTree.CheckboxTreeCellRenderer cellRenderer) {
    tree.setCellRenderer(cellRenderer);
    tree.setRootVisible(false);
    tree.setShowsRootHandles(true);
    tree.setLineStyleAngled();
    TreeUtil.installActions(tree);

    new ClickListener() {
      @Override
      public boolean onClick(MouseEvent e, int clickCount) {
        int row = tree.getRowForLocation(e.getX(), e.getY());
        if (row < 0) return false;
        final Object o = tree.getPathForRow(row).getLastPathComponent();
        if (!(o instanceof CheckedTreeNode)) return false;
        Rectangle rowBounds = tree.getRowBounds(row);
        cellRenderer.setBounds(rowBounds);
        Rectangle checkBounds = cellRenderer.myCheckbox.getBounds();
        checkBounds.setLocation(rowBounds.getLocation());

        if (checkBounds.height == 0) checkBounds.height = rowBounds.height;

        final CheckedTreeNode node = (CheckedTreeNode)o;
        if (checkBounds.contains(e.getPoint())) {
          if (node.isEnabled()) {
            toggleNode(node);
            tree.setSelectionRow(row);
            return true;
          }
        }

        return false;
      }
    }.installOn(this);

    addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (isToggleEvent(e)) {
          TreePath treePath = tree.getLeadSelectionPath();
          if (treePath == null) return;
          final Object o = treePath.getLastPathComponent();
          if (!(o instanceof CheckedTreeNode)) return;
          CheckedTreeNode firstNode = (CheckedTreeNode)o;
          boolean checked = toggleNode(firstNode);

          TreePath[] selectionPaths = tree.getSelectionPaths();
          for (int i = 0; selectionPaths != null && i < selectionPaths.length; i++) {
            final TreePath selectionPath = selectionPaths[i];
            final Object o1 = selectionPath.getLastPathComponent();
            if (!(o1 instanceof CheckedTreeNode)) continue;
            CheckedTreeNode node = (CheckedTreeNode)o1;
            checkNode(node, checked);
            ((DefaultTreeModel)tree.getModel()).nodeChanged(node);
          }

          e.consume();
        }
      }
    });

    tree.setSelectionRow(0);
  }

  private static boolean isToggleEvent(KeyEvent e) {
    return e.getKeyCode() == KeyEvent.VK_SPACE;
  }

  protected boolean toggleNode(CheckedTreeNode node) {
    boolean checked = !node.isChecked();
    checkNode(node, checked);

    // notify model listeners about model change
    final TreeModel model = getTree().getModel();
    model.valueForPathChanged(new TreePath(node.getPath()), node.getUserObject());

    return checked;
  }

  private void checkNode(CheckedTreeNode node, boolean checked) {
    adjustParentsAndChildren(node, checked);
    repaint();
  }

  private void adjustParentsAndChildren(final CheckedTreeNode node, final boolean checked) {
    changeNodeState(node, checked);
    if (!checked) {
      TreeNode parent = node.getParent();
      while (parent != null) {
        if (parent instanceof CheckedTreeNode) {
          changeNodeState((CheckedTreeNode)parent, false);
        }
        parent = parent.getParent();
      }
      uncheckChildren(node);
    }
    else {
      checkChildren(node);
    }
    repaint();
  }

  private static void changeNodeState(final CheckedTreeNode node, final boolean checked) {
    if (node.isChecked() != checked) {
      node.setChecked(checked);
    }
  }

  private static void uncheckChildren(final CheckedTreeNode node) {
    final Enumeration children = node.children();
    while (children.hasMoreElements()) {
      final Object o = children.nextElement();
      if (!(o instanceof CheckedTreeNode)) continue;
      CheckedTreeNode child = (CheckedTreeNode)o;
      changeNodeState(child, false);
      uncheckChildren(child);
    }
  }

  private static void checkChildren(final CheckedTreeNode node) {
    final Enumeration children = node.children();
    while (children.hasMoreElements()) {
      final Object o = children.nextElement();
      if (!(o instanceof CheckedTreeNode)) continue;
      CheckedTreeNode child = (CheckedTreeNode)o;
      changeNodeState(child, true);
      checkChildren(child);
    }
  }

  @SuppressWarnings("unchecked")
  public <T> T[] getCheckedNodes(final Class<T> nodeType) {
    final ArrayList<T> nodes = new ArrayList<T>();
    final Object root = getTree().getModel().getRoot();
    if (!(root instanceof CheckedTreeNode)) {
      throw new IllegalStateException("The root must be instance of the " + CheckedTreeNode.class.getName() + ": " + root.getClass().getName());
    }
    new Object() {
      @SuppressWarnings("unchecked")
      public void collect(CheckedTreeNode node) {
        if (node.isLeaf()) {
          Object userObject = node.getUserObject();
          if (node.isChecked() && userObject != null && nodeType.isAssignableFrom(userObject.getClass())) {
            final T value = (T)userObject;
            nodes.add(value);
          }
        }
        else {
          for (int i = 0; i < node.getChildCount(); i++) {
            final TreeNode child = node.getChildAt(i);
            if (child instanceof CheckedTreeNode) {
              collect((CheckedTreeNode)child);
            }
          }
        }
      }
    }.collect((CheckedTreeNode)root);
    T[] result = (T[])Array.newInstance(nodeType, nodes.size());
    nodes.toArray(result);
    return result;
  }
}
