/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
  private final ElementFilter myFilter;

  public ElementExtractorFilter(ElementFilter filter){
    myFilter = filter;
  }

  public ElementFilter getFilter(){
    return myFilter;
  }

  @Override
  public boolean isClassAcceptable(Class hintClass){
    return myFilter.isClassAcceptable(hintClass);
  }

  @Override
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
