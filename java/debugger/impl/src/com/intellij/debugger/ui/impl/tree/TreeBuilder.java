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
package com.intellij.debugger.ui.impl.tree;

import com.intellij.util.EventDispatcher;

import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

/**
 * User: lex
 * Date: Sep 10, 2003
 * Time: 6:56:51 PM
 */
public abstract class TreeBuilder implements TreeModel {
  private final Object userObject;
  private TreeBuilderNode myRoot;
  private final EventDispatcher<TreeModelListener> myDispatcher = EventDispatcher.create(TreeModelListener.class);

  protected TreeBuilder(Object userObject) {
    this.userObject = userObject;
  }

  public Object getUserObject() {
    return userObject;
  }

  public abstract void buildChildren(TreeBuilderNode node);
  public abstract boolean isExpandable (TreeBuilderNode node);

  public void setRoot(TreeBuilderNode root) {
    myRoot = root;
  }

  public Object getRoot() {
    return myRoot;
  }

  public int getChildCount(Object parent) {
    return ((TreeBuilderNode) parent).getChildCount();
  }

  public boolean isLeaf(Object node) {
    return ((TreeBuilderNode) node).isLeaf();
  }

  public void addTreeModelListener(TreeModelListener l) {
    myDispatcher.addListener(l);
  }

  public void removeTreeModelListener(TreeModelListener l) {
    myDispatcher.removeListener(l);
  }

  public Object getChild(Object parent, int index) {
    return ((TreeBuilderNode) parent).getChildAt(index);
  }

  public int getIndexOfChild(Object parent, Object child) {
    return ((TreeBuilderNode) parent).getIndex((TreeNode) child);
  }

  public void valueForPathChanged(TreePath path, Object newValue) {
    TreeBuilderNode  aNode = (TreeBuilderNode) path.getLastPathComponent();

    aNode.setUserObject(newValue);
    nodeChanged(aNode);
  }

  public void nodeChanged(TreeNode node) {
    TreeModelEvent event = null;
    TreeNode parent = node.getParent();
    if (parent != null) {
      int anIndex = parent.getIndex(node);
      event = new TreeModelEvent(this, getPathToRoot(parent, 0), new int[] {anIndex}, new Object[] {node});
    } else if (node == getRoot()) {
      event = new TreeModelEvent(this, getPathToRoot(node, 0), null, null);
    }
    if (event != null) {
      myDispatcher.getMulticaster().treeNodesChanged(event);
    }
  }

  public void nodeStructureChanged(TreeNode node) {
    TreeModelEvent event = new TreeModelEvent(this, getPathToRoot(node, 0), null, null);
    myDispatcher.getMulticaster().treeStructureChanged(event);
  }

  protected TreeNode[] getPathToRoot(TreeNode aNode, int depth) {
      TreeNode[]              retNodes;
      if(aNode == null) {
          if(depth == 0)
              return null;
          else
              retNodes = new TreeNode[depth];
      }
      else {
          depth++;
          if(aNode == myRoot)
              retNodes = new TreeNode[depth];
          else
              retNodes = getPathToRoot(aNode.getParent(), depth);
          retNodes[retNodes.length - depth] = aNode;
      }
      return retNodes;
  }

  public void removeNodeFromParent(TreeBuilderNode node) {
    final TreeBuilderNode parent = (TreeBuilderNode)node.getParent();
    if (parent != null) {
      parent.remove(node);
    }
  }

}
