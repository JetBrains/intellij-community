/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.util.ui.tree;

import javax.swing.*;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

public class IndexTreePathState implements TreePathState {
  private final TreePath mySelectionPath;
  private final int[] myIndicies;

  public IndexTreePathState(TreePath path) {
    mySelectionPath = path;
    myIndicies = pathToChildIndecies(path);
  }

  public TreePath getRestoredPath() {
    int aliveIndex = findLowestAliveNodeIndex(mySelectionPath);
    if (aliveIndex == mySelectionPath.getPathCount() - 1) return mySelectionPath;
    TreeNode aliveAncestor = (TreeNode) mySelectionPath.getPathComponent(aliveIndex);
    TreePath newSelection = TreeUtil.getPathFromRoot(aliveAncestor);
    int childrenLeft = aliveAncestor.getChildCount();
    if (childrenLeft != 0) {
      int newSelectedChildIndex = Math.min(myIndicies[aliveIndex + 1], childrenLeft - 1);
      newSelection = newSelection.pathByAddingChild(aliveAncestor.getChildAt(newSelectedChildIndex));
    }
    return newSelection;
  }

  public void restoreSelection(JTree tree) {
    TreeUtil.selectPath(tree, getRestoredPath());
  }

  private static int findLowestAliveNodeIndex(TreePath path) {
    Object[] nodes = path.getPath();
    for (int i = 1; i < nodes.length; i++) {
      TreeNode node = (TreeNode) nodes[i];
      if (node.getParent() == null) return i - 1;
    }
    return nodes.length - 1;
  }

  private static int[] pathToChildIndecies(TreePath path) {
    int[] result = new int[path.getPathCount()];
    for (int i = 0; i < path.getPathCount(); i++) {
      TreeNode node = (TreeNode) path.getPathComponent(i);
      TreeNode parent = node.getParent();
      result[i] = parent != null ? parent.getIndex(node) : 0;
    }
    return result;
  }
}
