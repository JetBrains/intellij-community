// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ui;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.util.containers.BidirectionalMap;
import com.intellij.util.containers.HashingStrategy;
import com.intellij.util.containers.Interner;
import com.intellij.util.containers.WeakInterner;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreeNode;
import java.util.*;

/**
 * Represents nodes of the {@link InspectionTree}.
 *
 * <ul>
 *   <li>Nodes for sorting:</li>
 *     <ul>
 *       <li>{@link InspectionRootNode}</li>
 *       <li>{@link InspectionPackageNode}</li>
 *       <li>{@link InspectionModuleNode}</li>
 *       <li>{@link InspectionSeverityGroupNode}</li>
 *       <li>{@link InspectionGroupNode} for <b>Editor | Inspections</b> categories</li>
 *     </ul>
 *   <li>Nodes for inspection tools:</li>
 *     <ul>
 *       <li> {@link InspectionNode}</li>
 *     </ul>
 *   <li>Nodes for problems:</li>
 *     <ul>
 *       <li>{@link SuppressableInspectionTreeNode}</li>
 *       <ul>
 *         <li>{@link RefElementNode} for the element concerned by the problem</li>
 *         <li>{@link ProblemDescriptionNode} for the description of the problem</li>
 *         <li>{@link com.intellij.codeInspection.offlineViewer.OfflineProblemDescriptorNode}</li>
 *       </ul>
 *     </ul>
 * </ul>
 */
public abstract class InspectionTreeNode implements TreeNode {
  private static final Interner<LevelAndCount[]> LEVEL_AND_COUNT_INTERNER = new WeakInterner<>(new HashingStrategy<>() {
    @Override
    public int hashCode(LevelAndCount[] object) {
      return Arrays.hashCode(object);
    }

    @Override
    public boolean equals(LevelAndCount[] o1, LevelAndCount[] o2) {
      return Arrays.equals(o1, o2);
    }
  });

  final ProblemLevels myProblemLevels = new ProblemLevels();
  volatile @Nullable Children myChildren;
  final InspectionTreeNode myParent;

  protected InspectionTreeNode(InspectionTreeNode parent) {
    myParent = parent;
  }

  protected boolean doesNeedInternProblemLevels() {
    return false;
  }

  public @Nullable Icon getIcon(boolean expanded) {
    return null;
  }

  LevelAndCount @NotNull [] getProblemLevels() {
    return myProblemLevels.getValue();
  }

  void dropProblemCountCaches() {
    InspectionTreeNode current = this;
    while (current != null) {
      current.myProblemLevels.drop();
      current = current.getParent();
    }
  }

  protected void visitProblemSeverities(@NotNull Object2IntMap<HighlightDisplayLevel> counter) {
    for (InspectionTreeNode child : getChildren()) {
      for (LevelAndCount levelAndCount : child.getProblemLevels()) {
        counter.mergeInt(levelAndCount.getLevel(), levelAndCount.getCount(), Math::addExact);
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

  public @Nullable @Nls(capitalization = Nls.Capitalization.Sentence) String getTailText() {
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

  public abstract @Nls String getPresentableText();

  public @NotNull List<? extends InspectionTreeNode> getChildren() {
    Children children = myChildren;
    return children == null ? Collections.emptyList() : List.of(children.myChildren);
  }

  @Override
  public InspectionTreeNode getParent() {
    return myParent;
  }

  @Override
  public int getChildCount() {
    return getChildren().size();
  }

  @Override
  public InspectionTreeNode getChildAt(int idx) {
    return getChildren().get(idx);
  }

  @Override
  public int getIndex(TreeNode node) {
    return Collections.binarySearch(getChildren(), (InspectionTreeNode)node, InspectionResultsViewComparator.INSTANCE);
  }

  @Override
  public boolean getAllowsChildren() {
    return true;
  }

  @Override
  public Enumeration<? extends TreeNode> children() {
    return Collections.enumeration(getChildren());
  }

  @Override
  public String toString() {
    return getPresentableText();
  }

  static final class Children {
    private static final InspectionTreeNode[] EMPTY_ARRAY = new InspectionTreeNode[0];

    volatile InspectionTreeNode[] myChildren = EMPTY_ARRAY;
    final BidirectionalMap<Object, InspectionTreeNode> myUserObject2Node = new BidirectionalMap<>();

    void clear() {
      myChildren = EMPTY_ARRAY;
      myUserObject2Node.clear();
    }
  }

  final class ProblemLevels {
    private volatile LevelAndCount[] myLevels;

    private LevelAndCount @NotNull [] compute() {
      Object2IntMap<HighlightDisplayLevel> counter=new Object2IntOpenHashMap<>();
      visitProblemSeverities(counter);
      LevelAndCount[] arr = new LevelAndCount[counter.size()];
      int i = 0;
      for (Object2IntMap.Entry<HighlightDisplayLevel> entry : counter.object2IntEntrySet()) {
        arr[i++] = new LevelAndCount(entry.getKey(), entry.getIntValue());
      }
      Comparator<LevelAndCount> comparator =
        Comparator.<LevelAndCount, HighlightSeverity>comparing(levelAndCount -> levelAndCount.getLevel().getSeverity()).reversed();
      Arrays.sort(arr, comparator);
      return doesNeedInternProblemLevels() ? LEVEL_AND_COUNT_INTERNER.intern(arr) : arr;
    }

    public LevelAndCount @NotNull [] getValue() {
      LevelAndCount[] result = myLevels;
      if (result == null) {
        myLevels = result = compute();
      }
      return result;
    }

    public void drop() {
      myLevels = null;
    }
  }
}
