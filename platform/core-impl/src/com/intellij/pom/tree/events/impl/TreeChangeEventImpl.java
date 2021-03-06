// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

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

import java.util.*;

@ApiStatus.Internal
public class TreeChangeEventImpl implements TreeChangeEvent{
  private final Map<ASTNode, TreeChangeImpl> myChangedElements = new LinkedHashMap<>();
  private final MultiMap<ASTNode, TreeChangeImpl> myChangesByAllParents = MultiMap.createSet();
  private final PomModelAspect myAspect;
  private final FileASTNode myFileElement;

  public TreeChangeEventImpl(@NotNull PomModelAspect aspect, @NotNull FileASTNode treeElement) {
    myAspect = aspect;
    myFileElement = treeElement;
  }

  @Override
  @NotNull
  public FileASTNode getRootElement() {
    return myFileElement;
  }

  @Override
  public ASTNode @NotNull [] getChangedElements() {
    return myChangedElements.keySet().toArray(ASTNode.EMPTY_ARRAY);
  }

  @Override
  public TreeChange getChangesByElement(@NotNull ASTNode element) {
    return myChangedElements.get(element);
  }

  public void addElementaryChange(@NotNull ASTNode parent) {
    TreeChangeImpl existing = myChangedElements.get(parent);
    if (existing != null) {
      existing.clearCache();
    }
    else if (!integrateIntoExistingChanges(parent)) {
      mergeChange(new TreeChangeImpl(parent));
    }
  }

  private boolean integrateIntoExistingChanges(ASTNode nextParent) {
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

  private void mergeChange(TreeChangeImpl nextChange) {
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

  private void registerChange(TreeChangeImpl nextChange) {
    myChangedElements.put(nextChange.getChangedParent(), nextChange);
    for (ASTNode eachParent : nextChange.getSuperParents()) {
      myChangesByAllParents.putValue(eachParent, nextChange);
    }
  }

  private void unregisterChange(TreeChangeImpl change) {
    myChangedElements.remove(change.getChangedParent());
    for (ASTNode superParent : change.getSuperParents()) {
      myChangesByAllParents.remove(superParent, change);
    }
  }

  /** @return a direct child of {@code ancestor} which contains {@code change} */
  @Nullable
  private static ASTNode findAncestorChild(@NotNull ASTNode ancestor, @NotNull TreeChangeImpl change) {
    List<ASTNode> superParents = change.getSuperParents();
    int index = superParents.indexOf(ancestor);
    return index < 0 ? null :
           index == 0 ? change.getChangedParent() :
           superParents.get(index - 1);
  }

  @Override
  @NotNull
  public PomModelAspect getAspect() {
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

  @NotNull
  public List<TreeChangeImpl> getSortedChanges() {
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

  public String toString() {
    return new ArrayList<>(myChangedElements.values()).toString();
  }

}
