package com.intellij.psi.filters.classes;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.InheritanceUtil;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 26.03.2003
 * Time: 21:02:40
 * To change this template use Options | File Templates.
 */
public class ClassAssignableToFilter extends ClassAssignableFilter{
  public ClassAssignableToFilter(String className){
    myClassName = className;
  }

  public ClassAssignableToFilter(PsiClass psiClass){
    myClass = psiClass;
  }

  public ClassAssignableToFilter(){}

  public boolean isAcceptable(Object aClass, PsiElement context){
    if(aClass instanceof PsiClass){
      PsiManager manager = ((PsiElement) aClass).getManager();
      final PsiClass psiClass = getPsiClass(manager, context.getResolveScope());
      return psiClass == aClass || ((PsiClass) aClass).isInheritor(psiClass, true);
    }
    return false;
  }

  public String toString(){
    return "class-assignable-to(" + getPsiClass(null, null) + ")";
  }
}
