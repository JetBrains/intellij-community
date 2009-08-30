package com.intellij.psi.filters.types;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ReflectionCache;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 17.04.2003
 * Time: 21:49:00
 * To change this template use Options | File Templates.
 */
public class TypeClassFilter implements ElementFilter{
  private final ElementFilter myFilter;

  public TypeClassFilter(ElementFilter _filter){
    myFilter = _filter;
  }

  public boolean isClassAcceptable(Class hintClass){
    return ReflectionCache.isAssignable(PsiType.class, hintClass);
  }

  public boolean isAcceptable(Object element, PsiElement context){
    if(element instanceof PsiType){
      final PsiType type = (PsiType) element;
      if(type instanceof PsiClassType && PsiUtil.resolveClassInType(type) != null){
        final PsiClass psiClass = PsiUtil.resolveClassInType(type);
        if(psiClass != null)
          return myFilter.isAcceptable(psiClass, context);
      }
    }
    return false;
  }

}
