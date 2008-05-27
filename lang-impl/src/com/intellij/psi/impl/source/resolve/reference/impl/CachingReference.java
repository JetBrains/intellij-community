/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.psi.impl.source.resolve.reference.impl;

import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public abstract class CachingReference implements PsiReference, EmptyResolveMessageProvider {
  public PsiElement resolve(){
    final PsiManager manager = getElement().getManager();
    if(manager instanceof PsiManagerImpl){
      return ((PsiManagerImpl)manager).getResolveCache().resolveWithCaching(this, MyResolver.INSTANCE, false, false);
    }
    return resolveInner();
  }

  @Nullable
  public abstract PsiElement resolveInner();

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

  private static class MyResolver implements ResolveCache.Resolver {
    private static MyResolver INSTANCE = new MyResolver();
    @Nullable
    public PsiElement resolve(PsiReference ref, boolean incompleteCode) {
      return ((CachingReference)ref).resolveInner();
    }
  }
  
}
