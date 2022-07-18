// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.java;

import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import static com.intellij.psi.impl.source.tree.JavaElementType.DECONSTRUCTION_LIST;

public class PsiDeconstructionListImpl extends CompositePsiElement implements PsiDeconstructionList {
  public PsiDeconstructionListImpl() {
    super(DECONSTRUCTION_LIST);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitDeconstructionList(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public @NotNull PsiPattern @NotNull [] getDeconstructionComponents() {
    PsiPattern[] children = PsiTreeUtil.getChildrenOfType(this, PsiPattern.class);
    if (children == null) {
      return PsiPattern.EMPTY;
    }
    return children;
  }

  @Override
  public String toString() {
    return "PsiDeconstructionList";
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    PsiPattern[] components = getDeconstructionComponents();
    for (PsiPattern component : components) {
      component.processDeclarations(processor, state, null, place);
    }
    return true;
  }
}
