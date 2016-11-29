/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.HashSet;
import java.util.Set;

public class TreeUpdatePass {
  private final DefaultMutableTreeNode myNode;

  private long myUpdateStamp;
  private boolean myExpired;

  private DefaultMutableTreeNode myCurrentNode;

  private final long myAllocation;

  private boolean myUpdateChildren = true;
  private boolean myUpdateStructure = true;
  private final Set<NodeDescriptor> myUpdatedDescriptors = new HashSet<>();

  public TreeUpdatePass(@NotNull final DefaultMutableTreeNode node) {
    myNode = node;
    myAllocation = System.currentTimeMillis();
  }

  public TreeUpdatePass setUpdateChildren(boolean updateChildren) {
    myUpdateChildren = updateChildren;
    return this;
  }

  public boolean isUpdateChildren() {
    return myUpdateChildren;
  }

  @NotNull
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

  @NonNls
  @Override
  public String toString() {
    return "TreeUpdatePass node=" + myNode + " structure=" + myUpdateStructure + " stamp=" + myUpdateStamp + " expired=" + myExpired + " currentNode=" + myCurrentNode + " allocation=" + myAllocation;
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

  public void addToUpdated(NodeDescriptor nodeDescriptor) {
    myUpdatedDescriptors.add(nodeDescriptor);
  }

  public boolean isUpdated(NodeDescriptor descriptor) {
    return myUpdatedDescriptors.contains(descriptor);
  }
}