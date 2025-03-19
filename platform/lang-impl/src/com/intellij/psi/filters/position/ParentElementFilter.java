// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi.filters.position;

import com.intellij.psi.PsiElement;
import com.intellij.psi.filters.ElementFilter;

public final class ParentElementFilter extends PositionElementFilter{
  private PsiElement myParent = null;
  private int myLevel = 1;
  public ParentElementFilter(ElementFilter filter){
    setFilter(filter);
  }

  public ParentElementFilter(ElementFilter filter, int level) {
    setFilter(filter);
    myLevel = level;
  }

  public ParentElementFilter(PsiElement parent){
    myParent = parent;
  }


  public ParentElementFilter(){}

  @Override
  public boolean isAcceptable(Object element, PsiElement scope){
    if (!(element instanceof PsiElement context)) return false;
    for(int i = 0; i < myLevel && context != null; i++){
       context = context.getContext();
    }
    if(context != null){
      if(myParent == null){
        return getFilter().isAcceptable(context, scope);
      }
      return myParent == context;
    }
    return false;
  }


  @Override
  public String toString(){
    return "parent(" +getFilter()+")";
  }

}
