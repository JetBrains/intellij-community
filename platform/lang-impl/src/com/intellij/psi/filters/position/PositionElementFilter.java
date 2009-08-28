package com.intellij.psi.filters.position;

import com.intellij.psi.PsiElement;
import com.intellij.psi.filters.ElementFilter;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 30.01.2003
 * Time: 13:51:02
 * To change this template use Options | File Templates.
 */
public abstract class PositionElementFilter implements ElementFilter {
  private ElementFilter myFilter;

  public void setFilter(ElementFilter filter){
    myFilter = filter;
  }

  public ElementFilter getFilter(){
    return myFilter;
  }

  public boolean isClassAcceptable(Class hintClass){
    return true;
  }

  protected static PsiElement getOwnerChild(final PsiElement scope, PsiElement element){
    while(element != null && element.getParent() != scope){
      element = element.getParent();
    }
    return element;
  }
}
