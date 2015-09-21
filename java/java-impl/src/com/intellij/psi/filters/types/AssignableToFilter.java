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
package com.intellij.psi.filters.types;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.filters.FilterUtil;
import com.intellij.psi.infos.CandidateInfo;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 28.01.2003
 * Time: 20:53:38
 * To change this template use Options | File Templates.
 */
public class AssignableToFilter implements ElementFilter {
  private final PsiType myType;

  public AssignableToFilter(@NotNull PsiType type){
    myType = type;
  }

  @Override
  public boolean isClassAcceptable(Class hintClass){
    return true;
  }

  @Override
  public boolean isAcceptable(Object element, PsiElement context){
    if(element == null) return false;
    if (element instanceof PsiType) return myType.isAssignableFrom((PsiType) element);
    PsiSubstitutor substitutor = null;
    if(element instanceof CandidateInfo){
      final CandidateInfo info = (CandidateInfo)element;
      substitutor = info.getSubstitutor();
      element = info.getElement();
    }

    PsiType typeByElement = FilterUtil.getTypeByElement((PsiElement)element, context);
    if(substitutor != null) typeByElement = substitutor.substitute(typeByElement);
    return typeByElement != null && typeByElement.isAssignableFrom(myType) && !typeByElement.equals(myType);
  }

  public String toString(){
    return "assignable-to(" + myType + ")";
  }
}
