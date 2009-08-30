package com.intellij.psi.filters.classes;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.util.PsiUtil;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 28.01.2003
 * Time: 20:37:26
 * To change this template use Options | File Templates.
 */
public class AnyInnerFilter implements ElementFilter{
  private final ElementFilter myFilter;
  public AnyInnerFilter(ElementFilter filter){
    myFilter = filter;
  }

  public ElementFilter getFilter(){
    return myFilter;
  }

  public boolean isAcceptable(Object classElement, PsiElement place){
    if(classElement instanceof PsiClass){
      final PsiClass[] inners = ((PsiClass)classElement).getInnerClasses();
      for (final PsiClass inner : inners) {
        if (inner.hasModifierProperty(PsiModifier.STATIC)
            && PsiUtil.isAccessible(inner, place, null)
            && myFilter.isAcceptable(inner, place)) {
          return true;
        }
      }
    }
    return false;
  }

  public boolean isClassAcceptable(Class hintClass) {
    return true;
  }

  public String toString(){
    return "any-inner(" + getFilter().toString() + ")";
  }
}
