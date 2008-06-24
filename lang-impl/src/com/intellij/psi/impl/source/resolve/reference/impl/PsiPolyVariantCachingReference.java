/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.psi.impl.source.resolve.reference.impl;

import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public abstract class PsiPolyVariantCachingReference implements PsiPolyVariantReference {
  @NotNull
  public final ResolveResult[] multiResolve(boolean incompleteCode) {
    final PsiManager manager = getElement().getManager();
    if(manager instanceof PsiManagerImpl){
      return ((PsiManagerImpl)manager).getResolveCache().resolveWithCaching(this, MyResolver.INSTANCE, true, incompleteCode);
    }
    return resolveInner(incompleteCode);
  }

  public PsiElement resolve() {
    ResolveResult[] results = multiResolve(false);
    return results.length == 1 ? results[0].getElement() : null;
  }

  @NotNull
  protected abstract ResolveResult[] resolveInner(boolean incompleteCode);

  public boolean isReferenceTo(final PsiElement element){
    return element.getManager().areElementsEquivalent(element, resolve());
  }

  public boolean isSoft(){
    return false;
  }

  @Nullable
  public static <T extends PsiElement> ElementManipulator<T> getManipulator(T currentElement){
    return ElementManipulators.getManipulator(currentElement);
  }

  private static class MyResolver implements ResolveCache.PolyVariantResolver<PsiPolyVariantReference> {
    private static MyResolver INSTANCE = new MyResolver();

    public ResolveResult[] resolve(PsiPolyVariantReference reference, boolean incompleteCode) {
      return ((PsiPolyVariantCachingReference)reference).resolveInner(incompleteCode);
    }
  }
}