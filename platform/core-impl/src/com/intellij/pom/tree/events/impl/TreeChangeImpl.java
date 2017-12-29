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

package com.intellij.pom.tree.events.impl;

import com.intellij.lang.ASTNode;
import com.intellij.pom.tree.events.ChangeInfo;
import com.intellij.pom.tree.events.TreeChange;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class TreeChangeImpl implements TreeChange {
  private final CompositeElement myParent;
  private final List<CompositeElement> mySuperParents;
  private final LinkedHashSet<TreeElement> myInitialChildren = new LinkedHashSet<>();
  private final Map<TreeElement, Integer> myInitialLengths = new HashMap<>();
  private final Set<TreeElement> myContentChangeChildren = new HashSet<>();
  private Map<TreeElement, ChangeInfoImpl> myChanges;

  public TreeChangeImpl(@NotNull CompositeElement parent) {
    myParent = parent;
    mySuperParents = JBIterable.generate(parent.getTreeParent(), TreeElement::getTreeParent).toList();
    for (TreeElement child : getCurrentChildren()) {
      myInitialChildren.add(child);
      myInitialLengths.put(child, child.getTextLength());
    }
  }

  List<CompositeElement> getSuperParents() {
    return mySuperParents;
  }

  @NotNull
  private JBIterable<TreeElement> getCurrentChildren() {
    return JBIterable.generate(myParent.getFirstChildNode(), TreeElement::getTreeNext);
  }

  int getCurrentStart() {
    return myParent.getStartOffset();
  }

  int getLengthDelta() {
    return getAllChanges().values().stream().mapToInt(ChangeInfoImpl::getLengthDelta).sum();
  }

  void clearCache() {
    myChanges = null;
  }

  private Map<TreeElement, ChangeInfoImpl> getAllChanges() {
    if (myChanges == null) {
      myChanges = new ChildrenDiff().calcChanges();
    }
    return myChanges;
  }
  
  private class ChildrenDiff {
    LinkedHashSet<TreeElement> currentChildren = getCurrentChildren().addAllTo(new LinkedHashSet<>());
    Iterator<TreeElement> itOld = myInitialChildren.iterator();
    Iterator<TreeElement> itNew = currentChildren.iterator();
    TreeElement oldChild, newChild;
    int oldOffset = 0;
    LinkedHashMap<TreeElement, ChangeInfoImpl> result = new LinkedHashMap<>();

    void advanceOld() {
      oldOffset += oldChild == null ? 0 : myInitialLengths.get(oldChild);
      oldChild = itOld.hasNext() ? itOld.next() : null;
    }

    void advanceNew() {
      newChild = itNew.hasNext() ? itNew.next() : null;
    }
    
    Map<TreeElement, ChangeInfoImpl> calcChanges() {
      advanceOld(); advanceNew();

      while (oldChild != null || newChild != null) {
        if (oldChild == newChild) {
          if (myContentChangeChildren.contains(oldChild)) {
            addChange(new ChangeInfoImpl(oldChild, oldChild, oldOffset, myInitialLengths.get(oldChild)));
          }
          advanceOld(); advanceNew();
        } else {
          boolean oldDisappeared = oldChild != null && !currentChildren.contains(oldChild);
          boolean newAppeared = newChild != null && !myInitialChildren.contains(newChild);
          addChange(new ChangeInfoImpl(oldDisappeared ? oldChild : null, newAppeared ? newChild : null,
                                       oldOffset,
                                       oldDisappeared ? myInitialLengths.get(oldChild) : 0));
          if (oldDisappeared) {
            advanceOld();
          }
          if (newAppeared) {
            advanceNew();
          }
        }
      }

      return result;
    }

    private void addChange(ChangeInfoImpl change) {
      result.put(change.getAffectedChild(), change);
      oldOffset += change.getLengthDelta();
    }
  }

  @NotNull
  public CompositeElement getChangedParent() {
    return myParent;
  }

  void fireEvents(PsiFile file) {
    int start = getCurrentStart();
    for (ChangeInfoImpl change : getAllChanges().values()) {
      change.fireEvent(start, file, myParent);
    }
  }

  @Override
  @NotNull
  public TreeElement[] getAffectedChildren() {
    return getAllChanges().keySet().toArray(TreeElement.EMPTY_ARRAY);
  }

  @Override
  public ChangeInfo getChangeByChild(ASTNode child) {
    return getAllChanges().get((TreeElement)child);
  }


  public String toString() {
    return myParent + ": " + getAllChanges().values();
  }

  void appendChanges(@NotNull TreeChangeImpl next) {
    myContentChangeChildren.addAll(next.myContentChangeChildren);
    clearCache();
  }

  public void markChildChanged(@NotNull TreeElement child, int lengthDelta) {
    myContentChangeChildren.add(child);
    Integer oldLength = myInitialLengths.get(child);
    if (oldLength != null && lengthDelta != 0) {
      myInitialLengths.put(child, oldLength - lengthDelta);
    }
    clearCache();
  }
}
