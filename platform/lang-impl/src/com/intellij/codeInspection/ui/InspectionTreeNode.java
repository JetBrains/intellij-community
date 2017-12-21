/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.intellij.codeInspection.ui;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.AtomicClearableLazyValue;
import com.intellij.util.containers.WeakInterner;
import com.intellij.util.ui.tree.TreeUtil;
import gnu.trove.TObjectHashingStrategy;
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
  private static final WeakInterner<LevelAndCount[]> LEVEL_AND_COUNT_INTERNER = new WeakInterner<>(new TObjectHashingStrategy<LevelAndCount[]>() {
    @Override
    public int computeHashCode(LevelAndCount[] object) {
      return Arrays.hashCode(object);
    }

    @Override
    public boolean equals(LevelAndCount[] o1, LevelAndCount[] o2) {
      return Arrays.equals(o1, o2);
    }
  });

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
      Arrays.sort(arr, Comparator.<LevelAndCount, HighlightSeverity>comparing(levelAndCount -> levelAndCount.getLevel().getSeverity())
        .reversed());
      return doesNeedInternProblemLevels() ? LEVEL_AND_COUNT_INTERNER.intern(arr) : arr;
    }
  };
  protected volatile InspectionTreeUpdater myUpdater;

  protected InspectionTreeNode(Object userObject) {
    super(userObject);
  }

  protected boolean doesNeedInternProblemLevels() {
    return false;
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

  protected void visitProblemSeverities(@NotNull TObjectIntHashMap<HighlightDisplayLevel> counter) {
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
      if (!allowDuplication && index >= 0) {
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

  protected void nodeAddedToTree() {
  }

  private void propagateUpdater(InspectionTreeUpdater updater) {
    if (myUpdater != null) return;
    myUpdater = updater;
    Enumeration enumeration = children();
    while (enumeration.hasMoreElements()) {
      InspectionTreeNode child = (InspectionTreeNode)enumeration.nextElement();
      child.propagateUpdater(updater);
      child.nodeAddedToTree();
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
