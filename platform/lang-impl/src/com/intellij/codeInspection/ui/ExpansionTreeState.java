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
package com.intellij.codeInspection.ui;

import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.util.*;

/**
 * @author Dmitry Batkovich
 */
@SuppressWarnings("unchecked")
public class ExpansionTreeState<T extends DefaultMutableTreeNode> {
  private final Set<Object> myExpandedUserObjects;
  @NotNull
  private final Comparator<T> myComparator;
  private TreeSelectionPath mySelectionPath;

  public ExpansionTreeState(@NotNull Comparator<T> comparator) {
    myExpandedUserObjects = new HashSet<>();
    myComparator = comparator;
  }

  public Set<Object> getExpandedUserObjects() {
    return myExpandedUserObjects;
  }

  public void setSelectionPath(TreePath selectionPath) {
    mySelectionPath = new TreeSelectionPath(selectionPath);
  }

  public void restoreExpansionAndSelection(JTree tree, boolean treeNodeMightChange) {
    restoreExpansionStatus((T)tree.getModel().getRoot(), tree);
    if (mySelectionPath != null) {
      mySelectionPath.restore(tree, treeNodeMightChange);
    } else {
      TreeUtil.selectFirstNode(tree);
    }
  }

  private void restoreExpansionStatus(T node, JTree tree) {
    if (getExpandedUserObjects().contains(node.getUserObject())) {
      //sortChildren(node);
      TreeNode[] pathToNode = node.getPath();
      tree.expandPath(new TreePath(pathToNode));
      Enumeration children = node.children();
      while (children.hasMoreElements()) {
        T childNode = (T)children.nextElement();
        restoreExpansionStatus(childNode, tree);
      }
    }
  }

  @SuppressWarnings("unchecked")
  private class TreeSelectionPath {
    private final Object[] myPath;
    private final int[] myIndices;

    public TreeSelectionPath(TreePath path) {
      myPath = path.getPath();
      myIndices = new int[myPath.length];
      for (int i = 0; i < myPath.length - 1; i++) {
        T node = (T)myPath[i];
        myIndices[i + 1] = getChildIndex(node, (T)myPath[i + 1]);
      }
    }

    private int getChildIndex(T node, T child) {
      int idx = 0;
      Enumeration children = node.children();
      while (children.hasMoreElements()) {
        T ch = (T)children.nextElement();
        if (ch == child) break;
        idx++;
      }
      return idx;
    }

    public void restore(JTree tree, boolean treeNodeMightChange) {
      tree.getSelectionModel().removeSelectionPaths(tree.getSelectionModel().getSelectionPaths());
      TreeUtil.selectPath(tree, restorePath(tree, treeNodeMightChange));
    }

    private TreePath restorePath(JTree tree, boolean treeNodeMightChange) {
      ArrayList<Object> newPath = new ArrayList<>();

      newPath.add(tree.getModel().getRoot());
      restorePath(newPath, 1, treeNodeMightChange);
      return new TreePath(newPath.toArray());
    }

    private void restorePath(ArrayList<Object> newPath, int idx, boolean treeNodeMightChange) {
      if (idx >= myPath.length) return;
      T oldNode = (T)myPath[idx];
      T newRoot = (T)newPath.get(idx - 1);

      Enumeration children = newRoot.children();
      while (children.hasMoreElements()) {
        T child = (T)children.nextElement();
        if (treeNodeMightChange ? myComparator.compare(child, oldNode) == 0 : child == oldNode) {
          newPath.add(child);
          restorePath(newPath, idx + 1, treeNodeMightChange);
          return;
        }
      }

      // Exactly same element not found. Trying to select somewhat near.
      int count = newRoot.getChildCount();
      if (count > 0) {
        if (myIndices[idx] < count) {
          newPath.add(newRoot.getChildAt(myIndices[idx]));
        }
        else {
          newPath.add(newRoot.getChildAt(count - 1));
        }
      }
    }
  }
}
