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

import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.openapi.vcs.FileStatus;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeNode;
import java.util.Enumeration;

/**
 * @author max
 */
public abstract class InspectionTreeNode extends DefaultMutableTreeNode {
  protected volatile InspectionTreeUpdater myUpdater;
  protected InspectionTreeNode  (Object userObject) {
    super(userObject);
  }

  @Nullable
  public abstract Icon getIcon(boolean expanded);

  public int getProblemCount() {
    int sum = 0;
    Enumeration enumeration = children();
    while (enumeration.hasMoreElements()) {
      InspectionTreeNode child = (InspectionTreeNode)enumeration.nextElement();
      sum += child.getProblemCount();
    }
    return sum;
  }

  public boolean isValid() {
    return true;
  }

  public boolean isResolved(ExcludedInspectionTreeNodesManager excludedManager){
    return excludedManager.isExcluded(this);
  }

  public boolean appearsBold() {
    return false;
  }

  public FileStatus getNodeStatus(){
    return FileStatus.NOT_CHANGED;
  }

  public void ignoreElement(ExcludedInspectionTreeNodesManager excludedManager) {
    excludedManager.exclude(this);
    Enumeration enumeration = children();
    while (enumeration.hasMoreElements()) {
      InspectionTreeNode child = (InspectionTreeNode)enumeration.nextElement();
      child.ignoreElement(excludedManager);
    }
  }

  public void amnesty(ExcludedInspectionTreeNodesManager excludedManager) {
    excludedManager.amnesty(this);
    Enumeration enumeration = children();
    while (enumeration.hasMoreElements()) {
      InspectionTreeNode child = (InspectionTreeNode)enumeration.nextElement();
      child.amnesty(excludedManager);
    }
  }

  @Override
  public void add(MutableTreeNode newChild) {
    super.add(newChild);
    if (myUpdater != null) {
      ((InspectionTreeNode)newChild).propagateUpdater(myUpdater);
      myUpdater.updateWithPreviewPanel(this);
    }
  }

  @Override
  public void insert(MutableTreeNode newChild, int childIndex) {
    super.insert(newChild, childIndex);
    if (myUpdater != null) {
      ((InspectionTreeNode)newChild).propagateUpdater(myUpdater);
      myUpdater.updateWithPreviewPanel(this);
    }
  }

  private void propagateUpdater(InspectionTreeUpdater updater) {
    if (myUpdater != null) return;
    myUpdater = updater;
    Enumeration enumeration = children();
    while (enumeration.hasMoreElements()) {
      InspectionTreeNode child = (InspectionTreeNode)enumeration.nextElement();
      child.propagateUpdater(updater);
    }
  }

  public RefEntity getContainingFileLocalEntity() {
    final Enumeration children = children();
    RefEntity current = null;
    while (children.hasMoreElements()) {
      InspectionTreeNode child = (InspectionTreeNode)children.nextElement();
      final RefEntity entity = child.getContainingFileLocalEntity();
      if (entity == null || current != null) {
        return null;
      }
      current = entity;
    }
    return current;
  }

  @Override
  public synchronized void setParent(MutableTreeNode newParent) {
    super.setParent(newParent);
  }

  @Override
  public synchronized TreeNode getParent() {
    return super.getParent();
  }
}
