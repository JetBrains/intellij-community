// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.scope.PsiScopeProcessor;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;

import static com.intellij.psi.SyntaxTraverser.psiTraverser;

public class PsiResourceListImpl extends CompositePsiElement implements PsiResourceList {
  public PsiResourceListImpl() {
    super(JavaElementType.RESOURCE_LIST);
  }

  @Override
  public int getResourceVariablesCount() {
    int count = 0;
    for (PsiElement child = getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child instanceof PsiResourceListElement) ++count;
    }
    return count;
  }

  @NotNull
  @Override
  public Iterator<PsiResourceListElement> iterator() {
    return psiTraverser().children(this).filter(PsiResourceListElement.class).iterator();
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitResourceList(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    return PsiImplUtil.processDeclarationsInResourceList(this, processor, state, lastParent);
  }

  @Override
  public void deleteChildInternal(@NotNull ASTNode child) {
    if (child.getPsi() instanceof PsiResourceListElement && getResourceVariablesCount() == 1) {
      getTreeParent().deleteChildInternal(this);
      return;
    }

    super.deleteChildInternal(child);
  }

  @Override
  public String toString() {
    return "PsiResourceList:" + getText();
  }
}