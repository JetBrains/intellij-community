// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.filters.element;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.filters.position.PositionElementFilter;
import com.intellij.psi.util.MethodSignatureUtil;

import java.lang.ref.SoftReference;

public class ExcludeDeclaredFilter extends PositionElementFilter{
  public ExcludeDeclaredFilter(ElementFilter filter){
    setFilter(filter);
  }

  SoftReference<PsiElement> myCachedVar = new SoftReference<>(null);
  SoftReference<PsiElement> myCurrentContext = new SoftReference<>(null);

  @Override
  public boolean isAcceptable(Object element, PsiElement context){
    PsiElement cachedVar = context;

    if(myCurrentContext.get() != context){
      myCurrentContext = new SoftReference<>(context);
      while(cachedVar != null && !(getFilter().isAcceptable(cachedVar, cachedVar.getContext())))
        cachedVar = cachedVar.getContext();
      myCachedVar = new SoftReference<>(cachedVar);
    }

    if (element instanceof PsiMethod currentMethod && myCachedVar.get() instanceof PsiMethod candidate)  {
      return !candidate.getManager().areElementsEquivalent(candidate, currentMethod) && !isOverridingMethod(currentMethod, candidate);
    }
    else if(element instanceof PsiClassType){
      final PsiClass psiClass = ((PsiClassType)element).resolve();
      return isAcceptable(psiClass, context);
    }
    else if(context != null){
      if(element instanceof PsiElement)
        return !context.getManager().areElementsEquivalent(myCachedVar.get(), (PsiElement)element);
      return true;
    }
    return true;
  }

  //TODO check exotic conditions like overriding method in package-private class from class in other package
  private static boolean isOverridingMethod(final PsiMethod method, final PsiMethod candidate) {
    if (method.getManager().areElementsEquivalent(method, candidate)) return false;
    if (!MethodSignatureUtil.areSignaturesEqual(method,candidate)) return false;
    final PsiClass candidateContainingClass = candidate.getContainingClass();
    return candidateContainingClass.isInheritor(method.getContainingClass(), true);
  }
}
