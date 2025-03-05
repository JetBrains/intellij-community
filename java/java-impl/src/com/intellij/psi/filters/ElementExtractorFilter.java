// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.filters;

import com.intellij.psi.PsiElement;
import com.intellij.psi.infos.CandidateInfo;

public class ElementExtractorFilter implements ElementFilter{
  private final ElementFilter myFilter;

  public ElementExtractorFilter(ElementFilter filter){
    myFilter = filter;
  }

  public ElementFilter getFilter(){
    return myFilter;
  }

  @Override
  public boolean isClassAcceptable(Class hintClass){
    return myFilter.isClassAcceptable(hintClass);
  }

  @Override
  public boolean isAcceptable(Object element, PsiElement context){
    if(element instanceof CandidateInfo candidateInfo) {
      final PsiElement psiElement = candidateInfo.getElement();
      
      return myFilter.isAcceptable(psiElement, context);
    }
    else if(element instanceof PsiElement)
      return myFilter.isAcceptable(element, context);
    return false;
  }


  @Override
  public String toString(){
    return getFilter().toString();
  }
}
