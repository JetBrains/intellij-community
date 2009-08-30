package com.intellij.psi.filters.classes;

import com.intellij.psi.PsiClass;
import com.intellij.psi.filters.OrFilter;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.util.ReflectionCache;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 28.01.2003
 * Time: 20:49:29
 * To change this template use Options | File Templates.
 */
public class ThisOrAnyInnerFilter extends OrFilter{
  public ThisOrAnyInnerFilter(ElementFilter filter){
    super(filter, new AnyInnerFilter(filter));
  }

  public boolean isClassAcceptable(Class aClass){
    return ReflectionCache.isAssignable(PsiClass.class, aClass);
  }

  public String toString(){
    return "this-or-any-inner(" + getFilters().get(0).toString() + ")";
  }
}
