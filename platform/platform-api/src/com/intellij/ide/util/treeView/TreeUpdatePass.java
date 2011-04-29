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
package com.intellij.ide.util.treeView;

import com.intellij.openapi.util.ActiveRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultMutableTreeNode;

public class TreeUpdatePass {

  private DefaultMutableTreeNode myNode;

  private ActiveRunnable myBefore;
  private ActiveRunnable myAfter;

  private long myUpdateStamp;
  private boolean myExpired;

  private DefaultMutableTreeNode myCurrentNode;

  private long myAllocation;

  private boolean myUpdateChildren = true;
  private boolean myUpdateStructure = true;

  public TreeUpdatePass(@NotNull final DefaultMutableTreeNode node, @Nullable final ActiveRunnable before, @Nullable final ActiveRunnable after) {
    myNode = node;
    myBefore = before;
    myAfter = after;
    myAllocation = System.currentTimeMillis();
  }

  public TreeUpdatePass(@NotNull final DefaultMutableTreeNode node) {
    this(node, null, null);
  }

  public TreeUpdatePass setUpdateChildren(boolean updateChildren) {
    myUpdateChildren = updateChildren;
    return this;
  }

  public boolean isUpdateChildren() {
    return myUpdateChildren;
  }

  public DefaultMutableTreeNode getNode() {
    return myNode;
  }

  public TreeUpdatePass setUpdateStamp(final long updateCount) {
    myUpdateStamp = updateCount;
    return this;
  }

  public long getUpdateStamp() {
    return myUpdateStamp;
  }

  public void expire() {
    myExpired = true;
  }

  public boolean isExpired() {
    return myExpired;
  }

  public DefaultMutableTreeNode getCurrentNode() {
    return myCurrentNode;
  }

  public void setCurrentNode(DefaultMutableTreeNode currentNode) {
    myCurrentNode = currentNode;
  }

  @Override
  public String toString() {
    return "TreUpdatePass node=" + myNode + " structure=" + myUpdateStructure + " stamp=" + myUpdateStamp + " expired=" + myExpired + " currentNode=" + myCurrentNode + " allocation=" + myAllocation;
  }

  public boolean willUpdate(@NotNull DefaultMutableTreeNode node) {
    @NotNull DefaultMutableTreeNode currentNode = myCurrentNode != null ? myCurrentNode : myNode;
    return node.isNodeAncestor(currentNode);
  }

  public TreeUpdatePass setUpdateStructure(boolean update) {
    myUpdateStructure = update;
    return this;
  }

  public boolean isUpdateStructure() {
    return myUpdateStructure;
  }
}