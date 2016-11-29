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

import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Dmitry Batkovich
 */
public class InspectionTreeState {
  private final Set<Object> myExpandedUserObjects = new HashSet<>();
  private InspectionTreeSelectionPath mySelectionPath;

  public Set<Object> getExpandedUserObjects() {
    return myExpandedUserObjects;
  }

  public void setSelectionPath(TreePath selectionPath) {
    mySelectionPath = new InspectionTreeSelectionPath(selectionPath);
  }

  public void restoreExpansionAndSelection(InspectionTree tree, boolean treeNodeMightChange) {
    restoreExpansionStatus((InspectionTreeNode)tree.getModel().getRoot(), tree);
    if (mySelectionPath != null) {
      mySelectionPath.restore(tree, treeNodeMightChange);
    } else {
      TreeUtil.selectFirstNode(tree);
    }
  }

  private void restoreExpansionStatus(InspectionTreeNode node, InspectionTree tree) {
    if (getExpandedUserObjects().contains(node.getUserObject())) {
      //sortChildren(node);
      TreeNode[] pathToNode = node.getPath();
      tree.expandPath(new TreePath(pathToNode));
      Enumeration children = node.children();
      while (children.hasMoreElements()) {
        InspectionTreeNode childNode = (InspectionTreeNode)children.nextElement();
        restoreExpansionStatus(childNode, tree);
      }
    }
  }

  private static class InspectionTreeSelectionPath {
    private final Object[] myPath;
    private final int[] myIndicies;

    public InspectionTreeSelectionPath(TreePath path) {
      myPath = path.getPath();
      myIndicies = new int[myPath.length];
      for (int i = 0; i < myPath.length - 1; i++) {
        InspectionTreeNode node = (InspectionTreeNode)myPath[i];
        myIndicies[i + 1] = getChildIndex(node, (InspectionTreeNode)myPath[i + 1]);
      }
    }

    private static int getChildIndex(InspectionTreeNode node, InspectionTreeNode child) {
      int idx = 0;
      Enumeration children = node.children();
      while (children.hasMoreElements()) {
        InspectionTreeNode ch = (InspectionTreeNode)children.nextElement();
        if (ch == child) break;
        idx++;
      }
      return idx;
    }

    public void restore(InspectionTree tree, boolean treeNodeMightChange) {
      tree.getSelectionModel().removeSelectionPaths(tree.getSelectionModel().getSelectionPaths());
      TreeUtil.selectPath(tree, restorePath(tree, treeNodeMightChange));
    }

    private TreePath restorePath(InspectionTree tree, boolean treeNodeMightChange) {
      ArrayList<Object> newPath = new ArrayList<>();

      newPath.add(tree.getModel().getRoot());
      restorePath(newPath, 1, treeNodeMightChange);
      return new TreePath(newPath.toArray());
    }

    private void restorePath(ArrayList<Object> newPath, int idx, boolean treeNodeMightChange) {
      if (idx >= myPath.length) return;
      InspectionTreeNode oldNode = (InspectionTreeNode)myPath[idx];
      InspectionTreeNode newRoot = (InspectionTreeNode)newPath.get(idx - 1);

      Enumeration children = newRoot.children();
      while (children.hasMoreElements()) {
        InspectionTreeNode child = (InspectionTreeNode)children.nextElement();
        if (treeNodeMightChange ? InspectionResultsViewComparator.getInstance().areEqual(child, oldNode) : child == oldNode) {
          newPath.add(child);
          restorePath(newPath, idx + 1, treeNodeMightChange);
          return;
        }
      }

      // Exactly same element not found. Trying to select somewhat near.
      int count = newRoot.getChildCount();
      if (count > 0) {
        if (myIndicies[idx] < count) {
          newPath.add(newRoot.getChildAt(myIndicies[idx]));
        }
        else {
          newPath.add(newRoot.getChildAt(count - 1));
        }
      }
    }
  }
}
