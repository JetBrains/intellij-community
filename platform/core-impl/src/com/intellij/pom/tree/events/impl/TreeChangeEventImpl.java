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

package com.intellij.pom.tree.events.impl;

import com.intellij.lang.ASTNode;
import com.intellij.pom.PomModelAspect;
import com.intellij.pom.event.PomChangeSet;
import com.intellij.pom.tree.events.TreeChange;
import com.intellij.pom.tree.events.TreeChangeEvent;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author ik
 */
public class TreeChangeEventImpl implements TreeChangeEvent{
  private final Map<CompositeElement, TreeChangeImpl> myChangedElements = new LinkedHashMap<>();
  private final MultiMap<CompositeElement, TreeChangeImpl> myChangesByAllParents = MultiMap.createSet();
  private final PomModelAspect myAspect;
  private final FileElement myFileElement;

  public TreeChangeEventImpl(@NotNull PomModelAspect aspect, @NotNull FileElement treeElement) {
    myAspect = aspect;
    myFileElement = treeElement;
  }

  @Override
  @NotNull
  public FileElement getRootElement() {
    return myFileElement;
  }

  @Override
  @NotNull
  public ASTNode[] getChangedElements() {
    return myChangedElements.keySet().toArray(ASTNode.EMPTY_ARRAY);
  }

  @Override
  public TreeChange getChangesByElement(@NotNull ASTNode element) {
    return myChangedElements.get((CompositeElement)element);
  }

  public void addElementaryChange(@NotNull CompositeElement parent) {
    TreeChangeImpl existing = myChangedElements.get(parent);
    if (existing != null) {
      existing.clearCache();
    }
    else if (!integrateIntoExistingChanges(parent)) {
      mergeChange(new TreeChangeImpl(parent));
    }
  }

  private boolean integrateIntoExistingChanges(CompositeElement nextParent) {
    for (CompositeElement eachParent : JBIterable.generate(nextParent, TreeElement::getTreeParent)) {
      CompositeElement superParent = eachParent.getTreeParent();
      TreeChangeImpl superChange = myChangedElements.get(superParent);
      if (superChange != null) {
        superChange.markChildChanged(eachParent, 0);
        return true;
      }
    }
    return false;
  }

  private void mergeChange(TreeChangeImpl nextChange) {
    CompositeElement newParent = nextChange.getChangedParent();

    for (TreeChangeImpl descendant : new ArrayList<>(myChangesByAllParents.get(newParent))) {
      TreeElement ancestorChild = findAncestorChild(newParent, descendant);
      if (ancestorChild != null) {
        nextChange.markChildChanged(ancestorChild, descendant.getLengthDelta());
      }

      unregisterChange(descendant);
    }

    registerChange(nextChange);
  }

  private void registerChange(TreeChangeImpl nextChange) {
    myChangedElements.put(nextChange.getChangedParent(), nextChange);
    for (CompositeElement eachParent : nextChange.getSuperParents()) {
      myChangesByAllParents.putValue(eachParent, nextChange);
    }
  }

  private void unregisterChange(TreeChangeImpl change) {
    myChangedElements.remove(change.getChangedParent());
    for (CompositeElement superParent : change.getSuperParents()) {
      myChangesByAllParents.remove(superParent, change);
    }
  }

  /** @return a direct child of {@code ancestor} which contains {@code change} */
  @Nullable
  private static TreeElement findAncestorChild(@NotNull CompositeElement ancestor, @NotNull TreeChangeImpl change) {
    List<CompositeElement> superParents = change.getSuperParents();
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
    Collection<TreeChangeImpl> changes = ContainerUtil.sorted(myChangedElements.values());
    for (TreeChangeImpl change : changes) {
      change.fireEvents((PsiFile)myFileElement.getPsi());
    }
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
