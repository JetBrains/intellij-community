// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.pom.tree.events.impl;

import com.intellij.lang.ASTNode;
import com.intellij.pom.tree.events.ChangeInfo;
import com.intellij.pom.tree.events.ChangeInfoKind;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.PsiTreeChangeEventImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Describes a single child-level AST change: holds the old and new child nodes, the offset in the parent's
 * original text, and the old/new text lengths. Also, responsible for firing the corresponding
 * {@link PsiTreeChangeEventImpl} ({@code childAdded}, {@code childRemoved}, {@code childReplaced},
 * or {@code childrenChanged}).
 *
 * @see TreeChangeImpl.ChildrenDiff
 */
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

  public @Nullable ASTNode getOldChildNode() {
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
  public @NotNull ChangeInfoKind getChangeType() {
    if (myOldChild == myNewChild) return ChangeInfoKind.ContentsChanged;
    if (myOldChild != null) return myNewChild == null ? ChangeInfoKind.Removed : ChangeInfoKind.Replaced;
    return ChangeInfoKind.Added;
  }

  @Override
  public @NotNull String toString() {
    return myOldChild + "(" + myOldLength + ")" + "->" + myNewChild + "(" + myNewLength + ") at " + myOffset;
  }

  int getLengthDelta() {
    return myNewLength - myOldLength;
  }

  @NotNull ASTNode getAffectedChild() {
    ASTNode result = myNewChild != null ? myNewChild : myOldChild;
    assert result != null : "At least one of oldChild/newChild must be non-null";
    return result;
  }

  void fireEvent(int parentStart, @NotNull PsiFile file, @NotNull ASTNode parent) {
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

  private static void childAdded(@NotNull PsiTreeChangeEventImpl e, @NotNull ASTNode child, @NotNull ASTNode parent) {
    e.setParent(parent.getPsi());
    e.setChild(child.getPsi());
    getPsiManagerEx(e).childAdded(e);
  }

  private void childRemoved(@NotNull PsiTreeChangeEventImpl e, @NotNull ASTNode child, @NotNull ASTNode parent) {
    e.setParent(parent.getPsi());
    e.setChild(child.getPsi());
    e.setOldLength(myOldLength);
    getPsiManagerEx(e).childRemoved(e);
  }

  private void childReplaced(@NotNull PsiTreeChangeEventImpl e,
                             @NotNull ASTNode oldChild,
                             @NotNull ASTNode newChild,
                             @NotNull ASTNode parent) {
    e.setParent(parent.getPsi());
    e.setOldChild(oldChild.getPsi());
    e.setChild(newChild.getPsi());
    e.setNewChild(newChild.getPsi());
    e.setOldLength(myOldLength);
    getPsiManagerEx(e).childReplaced(e);
  }

  static void childrenChanged(@NotNull PsiTreeChangeEventImpl e, @NotNull ASTNode parent, int oldLength) {
    e.setParent(parent.getPsi());
    e.setOldLength(oldLength);
    getPsiManagerEx(e).childrenChanged(e);
  }

  private static @NotNull PsiManagerEx getPsiManagerEx(@NotNull PsiTreeChangeEventImpl e) {
    return (PsiManagerEx)e.getSource();
  }
}
