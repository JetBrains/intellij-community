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

import com.intellij.pom.tree.events.ChangeInfo;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.PsiTreeChangeEventImpl;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ChangeInfoImpl implements ChangeInfo {
  @Nullable private final TreeElement myOldChild;
  @Nullable private final TreeElement myNewChild;
  private final int myOffset;
  private final int myOldLength;
  private final int myNewLength;

  ChangeInfoImpl(@Nullable TreeElement oldChild, @Nullable TreeElement newChild, int offset, int oldLength) {
    myOldChild = oldChild;
    myNewChild = newChild;
    myOffset = offset;
    myOldLength = oldLength;
    myNewLength = newChild != null ? newChild.getNotCachedLength() : 0;
  }

  public TreeElement getOldChild() {
    return myOldChild;
  }

  @Override
  public int getChangeType(){
    if (myOldChild == myNewChild) return CONTENTS_CHANGED;
    if (myOldChild != null) return myNewChild == null ? REMOVED : REPLACE;
    return ADD;
  }

  @Override
  public String toString() {
    return myOldChild + "(" + myOldLength + ")" + "->" + myNewChild + "(" + myNewLength + ") at " + myOffset;
  }

  int getLengthDelta() {
    return myNewLength - myOldLength;
  }

  TreeElement getAffectedChild() {
    return myNewChild != null ? myNewChild : myOldChild;
  }

  void fireEvent(int parentStart, PsiFile file, CompositeElement parent) {
    PsiTreeChangeEventImpl e = createEvent(file, myOffset + parentStart);

    if (myOldChild == myNewChild && myNewChild != null) {
      childrenChanged(e, myNewChild, myOldLength);
    }
    else if (myOldChild != null && myNewChild != null) {
      childReplaced(e, myOldChild, myNewChild, parent);
    }
    else if (myOldChild != null) {
      childRemoved(e, myOldChild, parent);
    }
    else if (myNewChild != null) {
      childAdded(e, myNewChild, parent);
    }
  }

  @NotNull
  static PsiTreeChangeEventImpl createEvent(PsiFile file, int offset) {
    PsiTreeChangeEventImpl e = new PsiTreeChangeEventImpl(file.getManager());
    e.setFile(file);
    e.setOffset(offset);
    return e;
  }

  boolean hasNoPsi() {
    return myOldChild != null && myOldChild.getPsi() == null || 
           myNewChild != null && myNewChild.getPsi() == null;
  }

  private static void childAdded(PsiTreeChangeEventImpl e, TreeElement child, CompositeElement parent) {
    e.setParent(parent.getPsi());
    e.setChild(child.getPsi());
    getPsiManagerImpl(e).childAdded(e);
  }

  private void childRemoved(PsiTreeChangeEventImpl e, TreeElement child, CompositeElement parent) {
    e.setParent(parent.getPsi());
    e.setChild(child.getPsi());
    e.setOldLength(myOldLength);
    getPsiManagerImpl(e).childRemoved(e);
  }

  private void childReplaced(PsiTreeChangeEventImpl e, TreeElement oldChild, TreeElement newChild, CompositeElement parent) {
    e.setParent(parent.getPsi());
    e.setOldChild(oldChild.getPsi());
    e.setChild(newChild.getPsi());
    e.setNewChild(newChild.getPsi());
    e.setOldLength(myOldLength);
    getPsiManagerImpl(e).childReplaced(e);
  }

  static void childrenChanged(PsiTreeChangeEventImpl e, TreeElement parent, int oldLength) {
    e.setParent(parent.getPsi());
    e.setOldLength(oldLength);
    getPsiManagerImpl(e).childrenChanged(e);
  }

  private static PsiManagerImpl getPsiManagerImpl(PsiTreeChangeEventImpl e) {
    return (PsiManagerImpl)e.getSource();
  }
}
