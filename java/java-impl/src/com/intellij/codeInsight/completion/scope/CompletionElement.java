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
package com.intellij.codeInsight.completion.scope;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 20.01.2003
 * Time: 16:17:14
 * To change this template use Options | File Templates.
 */
public class CompletionElement{
  private final PsiType myQualifier;
  private final PsiClass myQualifierClass;
  private final Object myElement;
  private final PsiSubstitutor mySubstitutor;

  public CompletionElement(PsiType qualifier, Object element, PsiSubstitutor substitutor, final PsiClass qualifierClass){
    myElement = element;
    myQualifier = qualifier;
    mySubstitutor = substitutor;
    myQualifierClass = qualifierClass;
  }

  public PsiSubstitutor getSubstitutor(){
    return mySubstitutor;
  }

  public Object getElement(){
    return myElement;
  }

  public Object getUniqueId(){
    final String name;
    if(myElement instanceof PsiClass){
      name = ((PsiClass)myElement).getQualifiedName();
    }
    else if(myElement instanceof PsiPackage){
      name = ((PsiPackage)myElement).getQualifiedName();
    }
    else if(myElement instanceof PsiMethod){
      return ((PsiMethod)myElement).getSignature(mySubstitutor);
    }
    else if (myElement instanceof PsiField) {
      final PsiField field = (PsiField)myElement;
      final String s = field.getName();
      if (myQualifierClass != null || !field.hasModifierProperty(PsiModifier.STATIC)) return "#" + s;
      return field.getContainingClass().getQualifiedName() + "#" + s;
    }
    else if(myElement instanceof PsiElement){
      name = PsiUtil.getName((PsiElement)myElement);
    }
    else{
      name = "";
    }

    return name;
  }

  public PsiType getQualifier(){
    return myQualifier;
  }
}
