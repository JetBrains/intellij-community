// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.sourceToSink.propagate;

import com.intellij.codeInspection.sourceToSink.NonMarkedElement;
import com.intellij.codeInspection.sourceToSink.TaintAnalyzer;
import com.intellij.codeInspection.sourceToSink.TaintValue;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.util.treeView.PresentableNodeDescriptor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class TaintNode extends PresentableNodeDescriptor<TaintNode> {

  private final SmartPsiElementPointer<PsiElement> myPsiElement;
  private final SmartPsiElementPointer<PsiElement> myRef;
  List<TaintNode> myCachedChildren;
  TaintValue myTaintValue = TaintValue.UNKNOWN;
  boolean isTaintFlowRoot;
  private boolean isExcluded;

  TaintNode(TaintNode parent, PsiElement psiElement, @Nullable PsiElement ref) {
    super(parent == null ? null : parent.myProject, parent);
    myPsiElement = psiElement == null ? null : SmartPointerManager.createPointer(psiElement);
    myRef = ref == null ? null : SmartPointerManager.createPointer(ref);
  }

  @Override
  public TaintNode getElement() {
    return this;
  }

  public PsiElement getRef() {
    return myRef == null ? null : myRef.getElement();
  }

  public @Nullable PsiElement getPsiElement() {
    return myPsiElement == null ? null : myPsiElement.getElement();
  }

  @Override
  protected void update(@NotNull PresentationData presentation) {
  }

  public List<TaintNode> calcChildren() {
    PsiElement psiElement = getPsiElement();
    if (psiElement == null) return null;
    if (myCachedChildren != null) return myCachedChildren;
    PsiElement elementRef = myRef == null ? null : myRef.getElement();
    if (elementRef == null) {
      return null;
    }
    myCachedChildren = propagate(psiElement, elementRef);
    if (isExcluded) myCachedChildren.forEach(c -> c.setExcluded(isExcluded));
    return myCachedChildren;
  }

  private @NotNull List<TaintNode> propagate(@NotNull PsiElement psiElement, @NotNull PsiElement elementRef) {
    TaintAnalyzer taintAnalyzer = new TaintAnalyzer();
    TaintValue taintValue = taintAnalyzer.fromElement(psiElement, elementRef, true);
    myTaintValue = taintValue;
    if (taintValue == TaintValue.UNTAINTED) return Collections.emptyList();
    if (taintValue == TaintValue.TAINTED) {
      markTainted();
      return Collections.emptyList();
    }
    Set<PsiElement> parents = collectParents();
    parents.add(psiElement);
    List<TaintNode> children = new ArrayList<>();
    for (NonMarkedElement nonMarkedElement : taintAnalyzer.getNonMarkedElements()) {
      if (parents.contains(nonMarkedElement.myNonMarked)) continue;
      TaintNode child = new TaintNode(this, nonMarkedElement.myNonMarked, nonMarkedElement.myRef);
      children.add(child);
    }
    return children;
  }

  private void markTainted() {
    myTaintValue = TaintValue.TAINTED;
    TaintNode parent = ObjectUtils.tryCast(getParentDescriptor(), TaintNode.class);
    if (parent != null) {
      List<TaintNode> siblings = parent.myCachedChildren;
      if (siblings != null && siblings.size() == 1) {
        parent.markTainted();
        return;
      }
    }
    isTaintFlowRoot = true;
  }

  private @NotNull Set<PsiElement> collectParents() {
    Set<PsiElement> parents = new HashSet<>();
    TaintNode parent = ObjectUtils.tryCast(getParentDescriptor(), TaintNode.class);
    while (parent != null) {
      PsiElement parentPsiElement = parent.getPsiElement();
      if (parentPsiElement == null) return parents;
      parents.add(parentPsiElement);
      parent = ObjectUtils.tryCast(parent.getParentDescriptor(), TaintNode.class);
    }
    return parents;
  }

  boolean isExcluded() {
    return isExcluded;
  }

  void setExcluded(boolean excluded) {
    isExcluded = excluded;
  }
}
