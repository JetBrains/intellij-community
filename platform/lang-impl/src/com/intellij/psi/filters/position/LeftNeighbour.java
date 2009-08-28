package com.intellij.psi.filters.position;

import com.intellij.psi.PsiElement;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.filters.FilterPositionUtil;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 06.02.2003
 * Time: 19:03:05
 * To change this template use Options | File Templates.
 */
public class LeftNeighbour extends PositionElementFilter{
  public LeftNeighbour(){}

  public LeftNeighbour(ElementFilter filter){
    setFilter(filter);
  }

  public boolean isAcceptable(Object element, PsiElement context){
    if (!(element instanceof PsiElement)) return false;
    final PsiElement previous = FilterPositionUtil.searchNonSpaceNonCommentBack((PsiElement) element);
    if(previous != null){
      return getFilter().isAcceptable(previous, context);
    }
    return false;
  }

  public String toString(){
    return "left(" +getFilter()+")";
  }
}

