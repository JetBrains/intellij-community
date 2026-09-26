// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.pom.tree.events.impl;

import com.intellij.lang.ASTNode;
import com.intellij.lang.FileASTNode;
import com.intellij.pom.PomModelAspect;
import com.intellij.pom.event.PomChangeSet;
import com.intellij.pom.tree.events.TreeChange;
import com.intellij.pom.tree.events.TreeChangeEvent;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Accumulates all AST child-level changes within a single POM transaction for one file.
 * <p>
 * Each mutation site reports the parent node via {@link #addElementaryChange}. The event maintains a set of
 * non-overlapping {@link TreeChangeImpl} records (one per changed parent) and keeps them deduplicated:
 * if a change is reported on a node whose ancestor already has a {@code TreeChangeImpl}, it is folded in;
 * if a new change covers an ancestor of existing ones, the descendants are absorbed.
 * <p>
 * When the transaction completes, {@link #fireEvents()} sorts the changes in document order
 * and dispatches PSI tree-change notifications ({@code childAdded}, {@code childRemoved}, etc.)
 * through {@link com.intellij.psi.impl.PsiManagerEx}.
 */
@ApiStatus.Internal
public class TreeChangeEventImpl implements TreeChangeEvent {
  private final @NotNull Map<ASTNode, TreeChangeImpl> myChangedElements = new LinkedHashMap<>();
  /** Index: ancestor node → all descendant {@link TreeChangeImpl} records underneath it. Used by {@link #mergeChange} for fast lookup. */
  private final @NotNull MultiMap<ASTNode, TreeChangeImpl> myChangesByAllParents = MultiMap.createSet();
  private final @NotNull PomModelAspect myAspect;
  private final @NotNull FileASTNode myFileElement;

  public TreeChangeEventImpl(@NotNull PomModelAspect aspect, @NotNull FileASTNode treeElement) {
    myAspect = aspect;
    myFileElement = treeElement;
  }

  @Override
  public @NotNull FileASTNode getRootElement() {
    return myFileElement;
  }

  @Override
  public @NotNull ASTNode @NotNull [] getChangedElements() {
    return myChangedElements.keySet().toArray(ASTNode.EMPTY_ARRAY);
  }

  @Override
  public @NotNull TreeChange getChangesByElement(@NotNull ASTNode element) {
    TreeChangeImpl change = myChangedElements.get(element);
    if (change == null) {
      throw new IllegalArgumentException("Element not found in change event: " + element);
    }
    return change;
  }

  /**
   * Notifies that the direct children of {@code parent} have been structurally modified
   * (a child added, removed, or replaced).
   * <p>
   * Three cases:
   * <ul>
   *   <li>This parent is already tracked — the cached diff is invalidated so it will be recomputed.</li>
   *   <li>An ancestor of this parent is already tracked — the change is folded into the ancestor.</li>
   *   <li>Otherwise — a new change record is created, absorbing any previously tracked descendant changes.</li>
   * </ul>
   */
  public void addElementaryChange(@NotNull ASTNode parent) {
    TreeChangeImpl existing = myChangedElements.get(parent);
    if (existing != null) {
      existing.clearCache();
    }
    else if (!integrateIntoExistingChanges(parent)) {
      mergeChange(new TreeChangeImpl(parent));
    }
  }

  private boolean integrateIntoExistingChanges(@NotNull ASTNode nextParent) {
    for (ASTNode eachParent : JBIterable.generate(nextParent, ASTNode::getTreeParent)) {
      ASTNode superParent = eachParent.getTreeParent();
      TreeChangeImpl superChange = myChangedElements.get(superParent);
      if (superChange != null) {
        superChange.markChildChanged(eachParent, 0);
        return true;
      }
    }
    return false;
  }

  private void mergeChange(@NotNull TreeChangeImpl nextChange) {
    ASTNode newParent = nextChange.getChangedParent();

    for (TreeChangeImpl descendant : new ArrayList<>(myChangesByAllParents.get(newParent))) {
      ASTNode ancestorChild = findAncestorChild(newParent, descendant);
      if (ancestorChild != null) {
        nextChange.markChildChanged(ancestorChild, descendant.getLengthDelta());
      }

      unregisterChange(descendant);
    }

    registerChange(nextChange);
  }

  private void registerChange(@NotNull TreeChangeImpl nextChange) {
    myChangedElements.put(nextChange.getChangedParent(), nextChange);
    for (ASTNode eachParent : nextChange.getSuperParents()) {
      myChangesByAllParents.putValue(eachParent, nextChange);
    }
  }

  private void unregisterChange(@NotNull TreeChangeImpl change) {
    myChangedElements.remove(change.getChangedParent());
    for (ASTNode superParent : change.getSuperParents()) {
      myChangesByAllParents.remove(superParent, change);
    }
  }

  /** @return a direct child of {@code ancestor} which contains {@code change} */
  private static @Nullable ASTNode findAncestorChild(@NotNull ASTNode ancestor, @NotNull TreeChangeImpl change) {
    List<ASTNode> superParents = change.getSuperParents();
    int index = superParents.indexOf(ancestor);
    return index < 0 ? null :
           index == 0 ? change.getChangedParent() :
           superParents.get(index - 1);
  }

  @Override
  public @NotNull PomModelAspect getAspect() {
    return myAspect;
  }

  @Override
  public void merge(@NotNull PomChangeSet next) {
    for (TreeChangeImpl change : ((TreeChangeEventImpl)next).myChangedElements.values()) {
      TreeChangeImpl existing = myChangedElements.get(change.getChangedParent());
      if (existing != null) {
        existing.appendChanges(change);
      }
      else if (!integrateIntoExistingChanges(change.getChangedParent())) {
        mergeChange(change);
      }
    }
  }

  public void fireEvents() {
    Collection<TreeChangeImpl> changes = getSortedChanges();
    for (TreeChangeImpl change : changes) {
      change.fireEvents((PsiFile)myFileElement.getPsi());
    }
  }

  public @NotNull @Unmodifiable List<TreeChangeImpl> getSortedChanges() {
    return ContainerUtil.sorted(myChangedElements.values());
  }

  @Override
  public void beforeNestedTransaction() {
    // compute changes and remember them, to prevent lazy computation to happen in another transaction
    // when more changes might have occurred but shouldn't count in this transaction
    for (TreeChangeImpl change : myChangedElements.values()) {
      change.getAffectedChildren();
    }
  }

  @Override
  public String toString() {
    return new ArrayList<>(myChangedElements.values()).toString();
  }
}
