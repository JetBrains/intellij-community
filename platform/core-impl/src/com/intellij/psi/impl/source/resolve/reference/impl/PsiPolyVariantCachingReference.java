// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.resolve.reference.impl;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import org.jetbrains.annotations.NotNull;

public abstract class PsiPolyVariantCachingReference implements PsiPolyVariantReference {
  @Override
  public final ResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
    PsiElement element = getElement();
    PsiFile file = element.getContainingFile();
    return ResolveCache.getInstance(file.getProject()).resolveWithCaching(this, MyResolver.INSTANCE, true, incompleteCode,file);
  }

  @Override
  public PsiElement resolve() {
    ResolveResult[] results = multiResolve(false);
    return results.length == 1 ? results[0].getElement() : null;
  }

  protected abstract ResolveResult @NotNull [] resolveInner(boolean incompleteCode, @NotNull PsiFile containingFile);

  @Override
  public boolean isReferenceTo(@NotNull PsiElement element) {
    return getElement().getManager().areElementsEquivalent(resolve(), element);
  }

  @Override
  public boolean isSoft(){
    return false;
  }

  private static class MyResolver implements ResolveCache.PolyVariantContextResolver<PsiPolyVariantReference> {
    private static final MyResolver INSTANCE = new MyResolver();

    @Override
    public ResolveResult @NotNull [] resolve(@NotNull PsiPolyVariantReference ref, @NotNull PsiFile containingFile, boolean incompleteCode) {
      return ((PsiPolyVariantCachingReference)ref).resolveInner(incompleteCode, containingFile);
    }
  }
}
