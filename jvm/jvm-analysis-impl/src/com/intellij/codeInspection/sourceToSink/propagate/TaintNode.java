// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.sourceToSink.propagate;

import com.intellij.codeInspection.sourceToSink.TaintValue;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import java.util.Set;
import java.util.Vector;

// TODO: equals and hashcode
class TaintNode extends DefaultMutableTreeNode {
  private final SmartPsiElementPointer<PsiModifierListOwner> myElement;
  private final SmartPsiElementPointer<PsiElement> myRef;
  TaintNode myParent;
  Set<TaintNode> myChildren;
  TaintValue myTaintValue;
  private boolean isExcluded;
  
  TaintNode() {
    myRef = null;
    myElement = null;
  }
  
  TaintNode(@NotNull PsiModifierListOwner element, @Nullable PsiElement ref,
            @Nullable TaintNode parent, @Nullable TaintValue taintValue) {
    super(element);
    myElement = SmartPointerManager.createPointer(element);
    myRef = ref == null ? null : SmartPointerManager.createPointer(ref);
    myParent = parent;
    myTaintValue = taintValue;
  }

  private void buildChildren() {
    if (children == null) {
      //noinspection UseOfObsoleteCollectionType
      children = myChildren == null ? new Vector<>() : new Vector<>(myChildren);
    }
  }

  @Nullable PsiModifierListOwner getElement() {
    return myElement == null ? null : myElement.getElement();
  }

  @Nullable PsiElement getRef() {
    return myRef == null ? null : myRef.getElement();
  }

  @Override
  public TreeNode getChildAt(int index) {
    buildChildren();
    return super.getChildAt(index);
  }

  @Override
  public int getChildCount() {
    buildChildren();
    return super.getChildCount();
  }

  @Override
  public boolean isLeaf() {
    if (children == null) {
      return false;
    }
    return super.isLeaf();
  }

  @Override
  public int getIndex(TreeNode aChild) {
    buildChildren();
    return super.getIndex(aChild);
  }

  boolean isExcluded() {
    return isExcluded;
  }

  void setExcluded(boolean excluded) {
    isExcluded = excluded;
  }
}
