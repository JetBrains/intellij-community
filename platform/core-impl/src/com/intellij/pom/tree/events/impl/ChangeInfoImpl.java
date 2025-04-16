// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.pom.tree.events.impl;

import com.intellij.lang.ASTNode;
import com.intellij.pom.tree.events.ChangeInfo;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.PsiTreeChangeEventImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ChangeInfoImpl implements ChangeInfo {
  private final @Nullable ASTNode myOldChild;
  private final @Nullable ASTNode myNewChild;
  private final int myOffset;
  private final int myOldLength;
  private final int myNewLength;

  ChangeInfoImpl(@Nullable ASTNode oldChild, @Nullable ASTNode newChild, int offset, int oldLength) {
    myOldChild = oldChild;
    myNewChild = newChild;
    myOffset = offset;
    myOldLength = oldLength;
    myNewLength = newChild != null ? newChild.getTextLength() : 0;
  }

  public ASTNode getOldChildNode() {
    return myOldChild;
  }

  public int getOffsetInParent() {
    return myOffset;
  }

  public int getOldLength() {
    return myOldLength;
  }

  public int getNewLength() {
    return myNewLength;
  }

  public @Nullable ASTNode getNewChild() {
    return myNewChild;
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

  ASTNode getAffectedChild() {
    return myNewChild != null ? myNewChild : myOldChild;
  }

  void fireEvent(int parentStart, PsiFile file, ASTNode parent) {
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

  static @NotNull PsiTreeChangeEventImpl createEvent(@NotNull PsiFile psiFile, int offset) {
    PsiTreeChangeEventImpl e = new PsiTreeChangeEventImpl(psiFile.getManager());
    e.setFile(psiFile);
    e.setOffset(offset);
    return e;
  }

  boolean hasNoPsi() {
    return myOldChild != null && myOldChild.getPsi() == null || 
           myNewChild != null && myNewChild.getPsi() == null;
  }

  private static void childAdded(PsiTreeChangeEventImpl e, ASTNode child, ASTNode parent) {
    e.setParent(parent.getPsi());
    e.setChild(child.getPsi());
    getPsiManagerImpl(e).childAdded(e);
  }

  private void childRemoved(PsiTreeChangeEventImpl e, ASTNode child, ASTNode parent) {
    e.setParent(parent.getPsi());
    e.setChild(child.getPsi());
    e.setOldLength(myOldLength);
    getPsiManagerImpl(e).childRemoved(e);
  }

  private void childReplaced(PsiTreeChangeEventImpl e, ASTNode oldChild, ASTNode newChild, ASTNode parent) {
    e.setParent(parent.getPsi());
    e.setOldChild(oldChild.getPsi());
    e.setChild(newChild.getPsi());
    e.setNewChild(newChild.getPsi());
    e.setOldLength(myOldLength);
    getPsiManagerImpl(e).childReplaced(e);
  }

  static void childrenChanged(PsiTreeChangeEventImpl e, ASTNode parent, int oldLength) {
    e.setParent(parent.getPsi());
    e.setOldLength(oldLength);
    getPsiManagerImpl(e).childrenChanged(e);
  }

  private static PsiManagerImpl getPsiManagerImpl(PsiTreeChangeEventImpl e) {
    return (PsiManagerImpl)e.getSource();
  }
}
