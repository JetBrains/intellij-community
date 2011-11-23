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
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 20.01.2003
 * Time: 16:17:14
 * To change this template use Options | File Templates.
 */
public class CompletionElement{
  private final Object myElement;
  private final PsiSubstitutor mySubstitutor;

  public CompletionElement(Object element, PsiSubstitutor substitutor) {
    myElement = element;
    mySubstitutor = substitutor;
  }

  public PsiSubstitutor getSubstitutor(){
    return mySubstitutor;
  }

  public Object getElement(){
    return myElement;
  }

  @Nullable
  Object getUniqueId(){
    if(myElement instanceof PsiClass){
      return ((PsiClass)myElement).getQualifiedName();
    }
    if(myElement instanceof PsiPackage){
      return ((PsiPackage)myElement).getQualifiedName();
    }
    if(myElement instanceof PsiMethod){
      return ((PsiMethod)myElement).getSignature(mySubstitutor);
    }
    if (myElement instanceof PsiVariable) {
      return "#" + ((PsiVariable)myElement).getName();
    }

    return null;
  }

}
