package com.intellij.psi.filters.position;

import com.intellij.psi.PsiElement;
import com.intellij.psi.filters.ElementFilter;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 11.02.2003
 * Time: 13:54:33
 * To change this template use Options | File Templates.
 */
public class SuperParentFilter extends PositionElementFilter{
  public SuperParentFilter(ElementFilter filter){
    setFilter(filter);
  }

  public SuperParentFilter(){}

  public boolean isAcceptable(Object element, PsiElement scope){
    if (!(element instanceof PsiElement)) return false;
    while((element = ((PsiElement) element).getParent()) != null){
      if(getFilter().isAcceptable(element, scope))
        return true;
    }
    return false;
  }


  public String toString(){
    return "super-parent(" +getFilter()+")";
  }
}
