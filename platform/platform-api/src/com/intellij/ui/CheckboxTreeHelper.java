// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.openapi.util.Key;
import com.intellij.ui.speedSearch.SpeedSearchSupply;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ArrayUtil;
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
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Enumeration;

public class CheckboxTreeHelper {
  private static final Key<Runnable> TREE_LISTENERS_REMOVER = Key.create("TREE_LISTENERS_REMOVER");
  public static final CheckboxTreeBase.CheckPolicy DEFAULT_POLICY = new CheckboxTreeBase.CheckPolicy(true, true, false, true);
  private final CheckboxTreeBase.CheckPolicy myCheckPolicy;
  private final EventDispatcher<CheckboxTreeListener> myEventDispatcher;

  public CheckboxTreeHelper(@NotNull CheckboxTreeBase.CheckPolicy checkPolicy, @NotNull EventDispatcher<CheckboxTreeListener> dispatcher) {
    myCheckPolicy = checkPolicy;
    myEventDispatcher = dispatcher;
  }

  public void initTree(final @NotNull Tree tree, JComponent mainComponent, CheckboxTreeBase.CheckboxTreeCellRendererBase cellRenderer) {
    removeTreeListeners(mainComponent);
    tree.setCellRenderer(cellRenderer);
    tree.setRootVisible(false);
    tree.setShowsRootHandles(true);
    TreeUtil.installActions(tree);

    KeyListener keyListener = setupKeyListener(tree, mainComponent);
    ClickListener clickListener = setupMouseListener(tree, mainComponent, cellRenderer);
    ComponentUtil.putClientProperty(mainComponent, TREE_LISTENERS_REMOVER, () -> {
      mainComponent.removeKeyListener(keyListener);
      clickListener.uninstall(mainComponent);
    });
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
      if (!(o instanceof CheckedTreeNode child)) continue;
      changeNodeState(child, false);
      uncheckChildren(child);
    }
  }

  private void checkChildren(final CheckedTreeNode node) {
    final Enumeration children = node.children();
    while (children.hasMoreElements()) {
      final Object o = children.nextElement();
      if (!(o instanceof CheckedTreeNode child)) continue;
      changeNodeState(child, true);
      checkChildren(child);
    }
  }

  private KeyListener setupKeyListener(final Tree tree, final JComponent mainComponent) {
    KeyListener listener = new KeyAdapter() {
      @Override
      public void keyPressed(@NotNull KeyEvent e) {
        if (isToggleEvent(e, mainComponent)) {
          TreePath[] selectionPaths = tree.getSelectionPaths();
          if (selectionPaths == null || selectionPaths.length == 0) return;

          TreePath treePath = tree.getLeadSelectionPath();
          if (treePath == null) return;

          int nodesToChange = selectionPaths.length - 1;
          if (!tree.isPathSelected(treePath)) {
            treePath = selectionPaths[nodesToChange];
            nodesToChange--;
          }

          final Object o = treePath.getLastPathComponent();
          if (!(o instanceof CheckedTreeNode firstNode)) return;
          if (!firstNode.isEnabled()) return;
          toggleNode(tree, firstNode);
          boolean checked = firstNode.isChecked();

          for (int i = 0; i <= nodesToChange; i++) {
            final TreePath selectionPath = selectionPaths[i];
            final Object o1 = selectionPath.getLastPathComponent();
            if (!(o1 instanceof CheckedTreeNode node)) continue;
            setNodeState(tree, node, checked);
          }

          e.consume();
        }
      }
    };
    mainComponent.addKeyListener(listener);
    return listener;
  }

  public static boolean isToggleEvent(KeyEvent e, JComponent mainComponent) {
    return e.getKeyCode() == KeyEvent.VK_SPACE && SpeedSearchSupply.getSupply(mainComponent) == null;
  }

  private ClickListener setupMouseListener(final Tree tree, JComponent mainComponent, final CheckboxTreeBase.CheckboxTreeCellRendererBase cellRenderer) {
    ClickListener listener = new ClickListener() {
      @Override
      public boolean onClick(@NotNull MouseEvent e, int clickCount) {
        int row = tree.getRowForLocation(e.getX(), e.getY());
        if (row < 0) return false;
        final Object o = tree.getPathForRow(row).getLastPathComponent();
        if (!(o instanceof CheckedTreeNode node)) return false;
        Rectangle rowBounds = tree.getRowBounds(row);
        cellRenderer.setBounds(rowBounds);
        cellRenderer.validate();
        Rectangle checkBounds = cellRenderer.myCheckbox.getBounds();
        checkBounds.setLocation(rowBounds.getLocation());

        if (checkBounds.height == 0) checkBounds.height = checkBounds.width = rowBounds.height;

        Rectangle clickableArea = myCheckPolicy.checkByRowClick? rowBounds: checkBounds;
        if (clickableArea.contains(e.getPoint()) && cellRenderer.myCheckbox.isVisible()) {
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
    };
    listener.installOn(mainComponent);
    return listener;
  }

  private static void removeTreeListeners(JComponent mainComponent) {
    Runnable remover = ComponentUtil.getClientProperty(mainComponent, TREE_LISTENERS_REMOVER);
    if (remover != null) remover.run();
  }

  public static <T> T[] getCheckedNodes(final Class<T> nodeType, final @Nullable Tree.NodeFilter<? super T> filter, final TreeModel model) {
    final ArrayList<T> nodes = new ArrayList<>();
    final Object root = model.getRoot();
    if (!(root instanceof CheckedTreeNode)) {
      throw new IllegalStateException(
        "The root must be instance of the " + CheckedTreeNode.class.getName() + ": " + root.getClass().getName());
    }
    new Object() {
      public void collect(CheckedTreeNode node) {
        if (node.isLeaf()) {
          Object userObject = node.getUserObject();
          if (node.isChecked() && userObject != null && nodeType.isAssignableFrom(userObject.getClass())) {
            //noinspection unchecked
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
    T[] result = ArrayUtil.newArray(nodeType, nodes.size());
    nodes.toArray(result);
    return result;
  }
}
