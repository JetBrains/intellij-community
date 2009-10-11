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

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import java.util.Enumeration;

/**
 * User: lex
 * Date: Sep 10, 2003
 * Time: 7:01:02 PM
 */
public abstract class TreeBuilderNode extends DefaultMutableTreeNode{
  private boolean  myChildrenBuilt = false;

  public TreeBuilderNode(Object userObject) {
    super(userObject);
  }

  abstract protected TreeBuilder getTreeBuilder();

  private void checkChildren() {
    synchronized (this) {
      if (myChildrenBuilt) {
        return;
      }
      myChildrenBuilt = true;
    }
    final TreeBuilder treeBuilder = getTreeBuilder();
    if(treeBuilder.isExpandable(this)) {
      treeBuilder.buildChildren(this);
    }
  }

  public void clear() {
    synchronized (this) {
      myChildrenBuilt = false;
    }
  }

  //TreeNode interface
  public int getChildCount() {
    checkChildren();
    return super.getChildCount();
  }

  public boolean getAllowsChildren() {
    checkChildren();
    return super.getAllowsChildren();
  }

  public boolean isLeaf() {
    return !getTreeBuilder().isExpandable(this);
  }

  public Enumeration children() {
    checkChildren();
    return super.children();
  }

  public Enumeration rawChildren() {
    return super.children();
  }

  public TreeNode getChildAt(int childIndex) {
    checkChildren();
    return super.getChildAt(childIndex);
  }

  public int getIndex(TreeNode node) {
    checkChildren();
    return super.getIndex(node);
  }
}
