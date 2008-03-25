package com.intellij.psi.scope.processor;

import com.intellij.psi.PsiElement;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.search.PsiElementProcessor;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 10.06.2003
 * Time: 14:33:56
 * To change this template use Options | File Templates.
 */
public class FilterElementProcessor implements PsiElementProcessor{
  private final List<PsiElement> myResults;
  private final ElementFilter myFilter;
  private final PsiElementProcessor myProcessor;

  public FilterElementProcessor(ElementFilter filter,  PsiElementProcessor processor, List container){
    myFilter = filter;
    myProcessor = processor;
    myResults = container;
  }


  public FilterElementProcessor(ElementFilter filter, List container){
    this(filter,  null, container);
  }

  public FilterElementProcessor(ElementFilter filter, PsiElementProcessor proc){
    this(filter, proc, new ArrayList());
  }


  public FilterElementProcessor(ElementFilter filter){
    this(filter, null, new ArrayList());
  }

  public boolean execute(PsiElement element){
    if(myFilter.isClassAcceptable(element.getClass()) && myFilter.isAcceptable(element, element.getParent())){
      if(myProcessor != null){
        return myProcessor.execute(element);
      }
      add(element);
    }
    return true;
  }

  protected void add(PsiElement element){
    myResults.add(element);
  }

  public List<PsiElement> getResults(){
    return myResults;
  }

  public boolean shouldProcess(Class elementClass){
    return myFilter.isClassAcceptable(elementClass);
  }
}
