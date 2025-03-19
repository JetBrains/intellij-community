// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.filters.types;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.filters.FilterUtil;
import com.intellij.psi.infos.CandidateInfo;
import org.jetbrains.annotations.NotNull;

public class AssignableToFilter implements ElementFilter {
  private final PsiType myType;

  public AssignableToFilter(@NotNull PsiType type){
    myType = type;
  }

  @Override
  public boolean isClassAcceptable(Class hintClass){
    return true;
  }

  @Override
  public boolean isAcceptable(Object element, PsiElement context){
    if(element == null) return false;
    if (element instanceof PsiType) return myType.isAssignableFrom((PsiType) element);
    PsiSubstitutor substitutor = null;
    if(element instanceof CandidateInfo info){
      substitutor = info.getSubstitutor();
      element = info.getElement();
    }

    PsiType typeByElement = FilterUtil.getTypeByElement((PsiElement)element, context);
    if(substitutor != null) typeByElement = substitutor.substitute(typeByElement);
    return typeByElement != null && typeByElement.isAssignableFrom(myType) && !typeByElement.equals(myType);
  }

  @Override
  public String toString(){
    return "assignable-to(" + myType + ")";
  }
}
