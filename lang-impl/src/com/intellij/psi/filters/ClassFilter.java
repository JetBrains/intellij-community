package com.intellij.psi.filters;

import com.intellij.psi.PsiElement;
import com.intellij.util.ReflectionCache;
import org.jetbrains.annotations.NonNls;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 28.01.2003
 * Time: 19:30:55
 * To change this template use Options | File Templates.
 */
public class ClassFilter implements ElementFilter{
  private final Class myFilter;
  private final boolean myAcceptableFlag;

  public ClassFilter(Class filter) {
    this(filter, true);
  }

  public ClassFilter(Class filter, boolean acceptableFlag){
    myFilter = filter;
    myAcceptableFlag = acceptableFlag;
  }

  public boolean isClassAcceptable(Class hintClass){
    return myAcceptableFlag ? filterMatches(hintClass) : !filterMatches(hintClass);
  }

  private boolean filterMatches(final Class hintClass) {
    if (ReflectionCache.isAssignable(myFilter,hintClass)) return true;
    return false;
  }

  public boolean isAcceptable(Object element, PsiElement context){
    if(element == null){
      return false;
    }
    return myAcceptableFlag ? filterMatches(element.getClass()) : !filterMatches(element.getClass());
  }

  @NonNls
  public String toString(){
    return "class(" + myFilter.getName() + ")";
  }
}

