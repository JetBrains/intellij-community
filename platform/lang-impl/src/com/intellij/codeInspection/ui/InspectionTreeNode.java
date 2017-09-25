/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.AtomicClearableLazyValue;
import com.intellij.util.ui.tree.TreeUtil;
import gnu.trove.TObjectIntHashMap;
import gnu.trove.TObjectIntProcedure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeNode;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Enumeration;

/**
 * @author max
 */
public abstract class InspectionTreeNode extends DefaultMutableTreeNode {
  protected final AtomicClearableLazyValue<LevelAndCount[]> myProblemLevels = new AtomicClearableLazyValue<LevelAndCount[]>() {
    @NotNull
    @Override
    protected LevelAndCount[] compute() {
      TObjectIntHashMap<HighlightDisplayLevel> counter = new TObjectIntHashMap<>();
      visitProblemSeverities(counter);
      LevelAndCount[] arr = new LevelAndCount[counter.size()];
      final int[] i = {0};
      counter.forEachEntry(new TObjectIntProcedure<HighlightDisplayLevel>() {
        @Override
        public boolean execute(HighlightDisplayLevel l, int c) {
          arr[i[0]++] = new LevelAndCount(l, c);
          return true;
        }
      });
      Arrays.sort(arr, Comparator.<LevelAndCount, HighlightSeverity>comparing(levelAndCount -> levelAndCount.getLevel().getSeverity()).reversed());
      return arr;
    }
  };
  protected volatile InspectionTreeUpdater myUpdater;
  protected InspectionTreeNode  (Object userObject) {
    super(userObject);
  }

  @Nullable
  public Icon getIcon(boolean expanded) {
    return null;
  }

  @NotNull
  public LevelAndCount[] getProblemLevels() {
    if (!isProblemCountCacheValid()) {
      dropProblemCountCaches();
    }
    return myProblemLevels.getValue();
  }

  private void dropProblemCountCaches() {
    InspectionTreeNode current = this;
    while (current != null) {
      current.myProblemLevels.drop();
      current = (InspectionTreeNode)current.getParent();
    }
  }

  protected boolean isProblemCountCacheValid() {
    return true;
  }

  protected void visitProblemSeverities(TObjectIntHashMap<HighlightDisplayLevel> counter) {
    Enumeration enumeration = children();
    while (enumeration.hasMoreElements()) {
      InspectionTreeNode child = (InspectionTreeNode)enumeration.nextElement();
      for (LevelAndCount levelAndCount : child.getProblemLevels()) {
        if (!counter.adjustValue(levelAndCount.getLevel(), levelAndCount.getCount())) {
          counter.put(levelAndCount.getLevel(), levelAndCount.getCount());
        }
      }
    }
  }

  public int getProblemCount(boolean allowSuppressed) {
    int sum = 0;
    Enumeration enumeration = children();
    while (enumeration.hasMoreElements()) {
      InspectionTreeNode child = (InspectionTreeNode)enumeration.nextElement();
      sum += child.getProblemCount(allowSuppressed);
    }
    return sum;
  }

  public boolean isValid() {
    return true;
  }

  public boolean isExcluded() {
    Enumeration enumeration = children();
    while (enumeration.hasMoreElements()) {
      InspectionTreeNode child = (InspectionTreeNode)enumeration.nextElement();
      if (!child.isExcluded()) {
        return false;
      }
    }
    return getChildCount() != 0;
  }

  public boolean appearsBold() {
    return false;
  }

  @Nullable
  public String getTailText() {
    return null;
  }

  public void excludeElement() {
    Enumeration enumeration = children();
    while (enumeration.hasMoreElements()) {
      InspectionTreeNode child = (InspectionTreeNode)enumeration.nextElement();
      child.excludeElement();
    }
  }

  public void amnestyElement() {
    Enumeration enumeration = children();
    while (enumeration.hasMoreElements()) {
      InspectionTreeNode child = (InspectionTreeNode)enumeration.nextElement();
      child.amnestyElement();
    }
  }

  public InspectionTreeNode insertByOrder(InspectionTreeNode child, boolean allowDuplication) {
    return ReadAction.compute(() -> {
      if (!allowDuplication) {
        int index = getIndex(child);
        if (index != -1) {
          return (InspectionTreeNode)getChildAt(index);
        }
      }
      int index = TreeUtil.indexedBinarySearch(this, child, InspectionResultsViewComparator.getInstance());
      if (!allowDuplication && index >= 0){
        return (InspectionTreeNode)getChildAt(index);
      }
      insert(child, Math.abs(index + 1));
      return child;
    });
  }

  @Override
  public void add(MutableTreeNode newChild) {
    super.add(newChild);
    if (myUpdater != null) {
      ((InspectionTreeNode)newChild).propagateUpdater(myUpdater);
      dropProblemCountCaches();
      myUpdater.updateWithPreviewPanel();
    }
  }

  @Override
  public void insert(MutableTreeNode newChild, int childIndex) {
    super.insert(newChild, childIndex);
    if (myUpdater != null) {
      ((InspectionTreeNode)newChild).propagateUpdater(myUpdater);
      dropProblemCountCaches();
      myUpdater.updateWithPreviewPanel();
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
  public synchronized TreeNode getParent() {
    return super.getParent();
  }

  @Override
  public synchronized void setParent(MutableTreeNode newParent) {
    super.setParent(newParent);
  }
}
