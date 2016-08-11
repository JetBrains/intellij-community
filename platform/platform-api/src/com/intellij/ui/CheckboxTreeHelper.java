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
package com.intellij.ui;

import com.intellij.ui.speedSearch.SpeedSearchSupply;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
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
public class CheckboxTreeHelper {
  public static final CheckboxTreeBase.CheckPolicy DEFAULT_POLICY = new CheckboxTreeBase.CheckPolicy(true, true, false, true);
  private final CheckboxTreeBase.CheckPolicy myCheckPolicy;
  private final EventDispatcher<CheckboxTreeListener> myEventDispatcher;

  public CheckboxTreeHelper(@NotNull CheckboxTreeBase.CheckPolicy checkPolicy, @NotNull EventDispatcher<CheckboxTreeListener> dispatcher) {
    myCheckPolicy = checkPolicy;
    myEventDispatcher = dispatcher;
  }

  public void initTree(@NotNull final Tree tree, JComponent mainComponent, CheckboxTreeBase.CheckboxTreeCellRendererBase cellRenderer) {
    tree.setCellRenderer(cellRenderer);
    tree.setRootVisible(false);
    tree.setShowsRootHandles(true);
    tree.setLineStyleAngled();
    TreeUtil.installActions(tree);

    setupKeyListener(tree, mainComponent);
    setupMouseListener(tree, mainComponent, cellRenderer);
  }

  public void setNodeState(Tree tree, CheckedTreeNode node, boolean checked) {
    changeNodeState(node, checked);
    adjustParentsAndChildren(node, checked);
    tree.repaint();

    // notify model listeners about model change
    final TreeModel model = tree.getModel();
    model.valueForPathChanged(new TreePath(node.getPath()), node.getUserObject());
  }

  private void toggleNode(Tree tree, CheckedTreeNode node) {
    setNodeState(tree, node, !node.isChecked());
  }

  private void adjustParentsAndChildren(final CheckedTreeNode node, final boolean checked) {
    if (!checked) {
      if (myCheckPolicy.uncheckParentWithUncheckedChild) {
        TreeNode parent = node.getParent();
        while (parent != null) {
          if (parent instanceof CheckedTreeNode) {
            changeNodeState((CheckedTreeNode)parent, false);
          }
          parent = parent.getParent();
        }
      }
      if (myCheckPolicy.uncheckChildrenWithUncheckedParent) {
        uncheckChildren(node);
      }
    }
    else {
      if (myCheckPolicy.checkChildrenWithCheckedParent) {
        checkChildren(node);
      }

      if (myCheckPolicy.checkParentWithCheckedChild) {
        TreeNode parent = node.getParent();
        while (parent != null) {
          if (parent instanceof CheckedTreeNode) {
            changeNodeState((CheckedTreeNode)parent, true);
          }
          parent = parent.getParent();
        }
      }
    }
  }

  private void changeNodeState(final CheckedTreeNode node, final boolean checked) {
    if (node.isChecked() != checked) {
      myEventDispatcher.getMulticaster().beforeNodeStateChanged(node);
      node.setChecked(checked);
      myEventDispatcher.getMulticaster().nodeStateChanged(node);
    }
  }

  private void uncheckChildren(final CheckedTreeNode node) {
    final Enumeration children = node.children();
    while (children.hasMoreElements()) {
      final Object o = children.nextElement();
      if (!(o instanceof CheckedTreeNode)) continue;
      CheckedTreeNode child = (CheckedTreeNode)o;
      changeNodeState(child, false);
      uncheckChildren(child);
    }
  }

  private void checkChildren(final CheckedTreeNode node) {
    final Enumeration children = node.children();
    while (children.hasMoreElements()) {
      final Object o = children.nextElement();
      if (!(o instanceof CheckedTreeNode)) continue;
      CheckedTreeNode child = (CheckedTreeNode)o;
      changeNodeState(child, true);
      checkChildren(child);
    }
  }

  private void setupKeyListener(final Tree tree, final JComponent mainComponent) {
    mainComponent.addKeyListener(new KeyAdapter() {
      public void keyPressed(@NotNull KeyEvent e) {
        if (isToggleEvent(e, mainComponent)) {
          TreePath treePath = tree.getLeadSelectionPath();
          if (treePath == null) return;
          final Object o = treePath.getLastPathComponent();
          if (!(o instanceof CheckedTreeNode)) return;
          CheckedTreeNode firstNode = (CheckedTreeNode)o;
          if (!firstNode.isEnabled()) return;
          toggleNode(tree, firstNode);
          boolean checked = firstNode.isChecked();

          TreePath[] selectionPaths = tree.getSelectionPaths();
          for (int i = 0; selectionPaths != null && i < selectionPaths.length; i++) {
            final TreePath selectionPath = selectionPaths[i];
            final Object o1 = selectionPath.getLastPathComponent();
            if (!(o1 instanceof CheckedTreeNode)) continue;
            CheckedTreeNode node = (CheckedTreeNode)o1;
            setNodeState(tree, node, checked);
          }

          e.consume();
        }
      }
    });
  }

  private static boolean isToggleEvent(KeyEvent e, JComponent mainComponent) {
    return e.getKeyCode() == KeyEvent.VK_SPACE && SpeedSearchSupply.getSupply(mainComponent) == null;
  }

  private void setupMouseListener(final Tree tree, JComponent mainComponent, final CheckboxTreeBase.CheckboxTreeCellRendererBase cellRenderer) {
    new ClickListener() {
      @Override
      public boolean onClick(@NotNull MouseEvent e, int clickCount) {
        int row = tree.getRowForLocation(e.getX(), e.getY());
        if (row < 0) return false;
        final Object o = tree.getPathForRow(row).getLastPathComponent();
        if (!(o instanceof CheckedTreeNode)) return false;
        Rectangle rowBounds = tree.getRowBounds(row);
        cellRenderer.setBounds(rowBounds);
        Rectangle checkBounds = cellRenderer.myCheckbox.getBounds();
        checkBounds.setLocation(rowBounds.getLocation());

        if (checkBounds.height == 0) checkBounds.height = checkBounds.width = rowBounds.height;

        final CheckedTreeNode node = (CheckedTreeNode)o;
        if (checkBounds.contains(e.getPoint())) {
          if (node.isEnabled()) {
            toggleNode(tree, node);
            tree.setSelectionRow(row);
            return true;
          }
        }
        else if (clickCount > 1 && clickCount % 2 == 0) {
          myEventDispatcher.getMulticaster().mouseDoubleClicked(node);
          return true;
        }

        return false;
      }
    }.installOn(mainComponent);
  }

  @SuppressWarnings("unchecked")
  public static <T> T[] getCheckedNodes(final Class<T> nodeType, @Nullable final Tree.NodeFilter<T> filter, final TreeModel model) {
    final ArrayList<T> nodes = new ArrayList<>();
    final Object root = model.getRoot();
    if (!(root instanceof CheckedTreeNode)) {
      throw new IllegalStateException(
        "The root must be instance of the " + CheckedTreeNode.class.getName() + ": " + root.getClass().getName());
    }
    new Object() {
      @SuppressWarnings("unchecked")
      public void collect(CheckedTreeNode node) {
        if (node.isLeaf()) {
          Object userObject = node.getUserObject();
          if (node.isChecked() && userObject != null && nodeType.isAssignableFrom(userObject.getClass())) {
            final T value = (T)userObject;
            if (filter != null && !filter.accept(value)) return;
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
