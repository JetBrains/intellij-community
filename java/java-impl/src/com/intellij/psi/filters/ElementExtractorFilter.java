package com.intellij.psi.filters;

import com.intellij.psi.PsiElement;
import com.intellij.psi.infos.CandidateInfo;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 17.07.2003
 * Time: 13:43:17
 * To change this template use Options | File Templates.
 */
public class ElementExtractorFilter implements ElementFilter{
  ElementFilter myFilter;

  public ElementExtractorFilter(){}

  public ElementExtractorFilter(ElementFilter filter){
    myFilter = filter;
  }

  public void setFilter(ElementFilter filter){
    myFilter = filter;
  }

  public ElementFilter getFilter(){
    return myFilter;
  }

  public boolean isClassAcceptable(Class hintClass){
    return myFilter.isClassAcceptable(hintClass);
  }

  public boolean isAcceptable(Object element, PsiElement context){
    if(element instanceof CandidateInfo) {
      final CandidateInfo candidateInfo = (CandidateInfo)element;
      final PsiElement psiElement = candidateInfo.getElement();
      
      return myFilter.isAcceptable(psiElement, context);
    }
    else if(element instanceof PsiElement)
      return myFilter.isAcceptable(element, context);
    return false;
  }


  public String toString(){
    return getFilter().toString();
  }
}
