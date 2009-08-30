package com.intellij.psi.filters.classes;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.util.ReflectionCache;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 28.01.2003
 * Time: 21:00:45
 * To change this template use Options | File Templates.
 */
public class InterfaceFilter implements ElementFilter{
  public boolean isClassAcceptable(Class hintClass){
    return ReflectionCache.isAssignable(PsiClass.class, hintClass);
  }

  public boolean isAcceptable(Object element, PsiElement context){
    return element instanceof PsiClass && ((PsiClass)element).isInterface();
  }

  public String toString(){
    return "interface";
  }
}
