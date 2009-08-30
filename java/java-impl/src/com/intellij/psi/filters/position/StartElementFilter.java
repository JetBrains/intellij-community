package com.intellij.psi.filters.position;

import com.intellij.openapi.util.InvalidDataException;
import com.intellij.psi.PsiElement;
import com.intellij.psi.filters.FilterUtil;
import org.jdom.Element;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 30.01.2003
 * Time: 13:09:39
 * To change this template use Options | File Templates.
 */
public class StartElementFilter extends PositionElementFilter{
  public boolean isAcceptable(Object element, PsiElement context){
    if (!(element instanceof PsiElement)) return false;
    return FilterUtil.getPreviousElement((PsiElement) element, false) == null;
  }

  public String toString(){
    return "start";
  }
}
