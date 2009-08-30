package com.intellij.psi.filters.classes;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.util.ReflectionCache;
import org.jetbrains.annotations.NonNls;

public class EnumOrAnnotationTypeFilter implements ElementFilter{

  public boolean isClassAcceptable(Class hintClass){
    return ReflectionCache.isAssignable(PsiClass.class, hintClass);
  }

  public boolean isAcceptable(Object element, PsiElement context){
    if(element instanceof PsiClass){
      return ((PsiClass)element).isEnum() || ((PsiClass)element).isAnnotationType();
    }
    return false;
  }

  @NonNls
  public String toString(){
    return "enum or annotation type";
  }
}
