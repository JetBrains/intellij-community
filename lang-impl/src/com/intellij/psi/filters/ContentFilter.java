package com.intellij.psi.filters;

import com.intellij.psi.PsiElement;
import com.intellij.psi.filters.position.PositionElementFilter;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 13.02.2003
 * Time: 12:17:49
 * To change this template use Options | File Templates.
 */
public class ContentFilter extends PositionElementFilter{
  public ContentFilter(ElementFilter filter){
    setFilter(filter);
  }

  public boolean isAcceptable(Object element, PsiElement scope){
    if (!(element instanceof PsiElement)) return false;
    PsiElement currentChild = ((PsiElement) element).getFirstChild();
    while(currentChild != null){
      if(getFilter().isAcceptable(currentChild, ((PsiElement) element))){
        return true;
      }
      currentChild = currentChild.getNextSibling();
    }
    return false;
  }

  public String toString(){
    return "content(" + getFilter().toString() + ")";
  }
}
