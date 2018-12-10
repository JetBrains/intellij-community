// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInspection.ui;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.AtomicClearableLazyValue;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.WeakInterner;
import gnu.trove.TObjectHashingStrategy;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreeNode;
import java.util.*;

/**
 * @author max
 */
public abstract class InspectionTreeNode implements TreeNode {
  static final InspectionTreeNode[] EMPTY_ARRAY = new InspectionTreeNode[0];
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

  final AtomicClearableLazyValue<LevelAndCount[]> myProblemLevels = new AtomicClearableLazyValue<LevelAndCount[]>() {
    @NotNull
    @Override
    protected LevelAndCount[] compute() {
      TObjectIntHashMap<HighlightDisplayLevel> counter = new TObjectIntHashMap<>();
      visitProblemSeverities(counter);
      LevelAndCount[] arr = new LevelAndCount[counter.size()];
      final int[] i = {0};
      counter.forEachEntry((l, c) -> {
        arr[i[0]++] = new LevelAndCount(l, c);
        return true;
      });
      Arrays.sort(arr, Comparator.<LevelAndCount, HighlightSeverity>comparing(levelAndCount -> levelAndCount.getLevel().getSeverity())
        .reversed());
      return doesNeedInternProblemLevels() ? LEVEL_AND_COUNT_INTERNER.intern(arr) : arr;
    }
  };
  @NotNull
  private final InspectionTreeModel myModel;

  protected InspectionTreeNode(@NotNull InspectionTreeModel model) {
    myModel = model;
  }

  protected boolean doesNeedInternProblemLevels() {
    return false;
  }

  @Nullable
  public Icon getIcon(boolean expanded) {
    return null;
  }

  @NotNull
  LevelAndCount[] getProblemLevels() {
    if (!isProblemCountCacheValid()) {
      dropProblemCountCaches();
    }
    return myProblemLevels.getValue();
  }

  void dropProblemCountCaches() {
    InspectionTreeNode current = this;
    while (current != null && myModel.getRoot() != current) {
      current.myProblemLevels.drop();
      current = current.getParent();
    }
  }

  protected boolean isProblemCountCacheValid() {
    return true;
  }

  protected void visitProblemSeverities(@NotNull TObjectIntHashMap<HighlightDisplayLevel> counter) {
    for (InspectionTreeNode child : getChildren()) {
      for (LevelAndCount levelAndCount : child.getProblemLevels()) {
        if (!counter.adjustValue(levelAndCount.getLevel(), levelAndCount.getCount())) {
          counter.put(levelAndCount.getLevel(), levelAndCount.getCount());
        }
      }
    }
  }

  public boolean isValid() {
    return true;
  }

  public boolean isExcluded() {
    List<? extends InspectionTreeNode> children = getChildren();
    for (InspectionTreeNode child : children) {
      if (!child.isExcluded()) {
        return false;
      }
    }

    return !children.isEmpty() ;
  }

  public boolean appearsBold() {
    return false;
  }

  @Nullable
  public String getTailText() {
    return null;
  }

  public void excludeElement() {
    for (InspectionTreeNode child : getChildren()) {
      child.excludeElement();
    }
    dropProblemCountCaches();
  }

  public void amnestyElement() {
    for (InspectionTreeNode child : getChildren()) {
      child.amnestyElement();
    }
    dropProblemCountCaches();
  }

  public void remove(int childIndex) {
    myModel.removeChild(this, childIndex);
    dropProblemCountCaches();
  }

  public RefEntity getContainingFileLocalEntity() {
    RefEntity current = null;
    for (InspectionTreeNode child : getChildren()) {
      final RefEntity entity = child.getContainingFileLocalEntity();
      if (entity == null || current != null) {
        return null;
      }
      current = entity;
    }
    return current;
  }

  @Override
  public boolean isLeaf() {
    return getChildren().isEmpty();
  }

  public abstract String getPresentableText();

  @NotNull
  public List<? extends InspectionTreeNode> getChildren() {
    return ObjectUtils.notNull(myModel.getChildren(this), Collections.emptyList());
  }

  @Override
  public InspectionTreeNode getParent() {
    return myModel.getParent(this);
  }

  @Override
  public int getChildCount() {
    return getChildren().size();
  }

  @Override
  public InspectionTreeNode getChildAt(int idx) {
    return getChildren().get(idx);
  }

  public void removeAllChildren() {
    myModel.removeChildren(this);
  }

  @Override
  public int getIndex(TreeNode node) {
    return myModel.getIndexOfChild(this, node);
  }

  @Override
  public boolean getAllowsChildren() {
    return true;
  }

  @Override
  public Enumeration children() {
    return Collections.enumeration(getChildren());
  }

  @Override
  public String toString() {
    return getPresentableText();
  }
}
