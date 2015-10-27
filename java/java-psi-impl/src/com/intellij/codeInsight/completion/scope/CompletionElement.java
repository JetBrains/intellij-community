/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureUtil;
import org.jetbrains.annotations.NotNull;
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
  private final Object myEqualityObject;

  public CompletionElement(Object element, PsiSubstitutor substitutor) {
    myElement = element;
    mySubstitutor = substitutor;
    myEqualityObject = getUniqueId();
  }

  public PsiSubstitutor getSubstitutor(){
    return mySubstitutor;
  }

  public Object getElement(){
    return myElement;
  }

  @Nullable
  private Object getUniqueId(){
    if(myElement instanceof PsiClass){
      String qName = ((PsiClass)myElement).getQualifiedName();
      return qName == null ? ((PsiClass)myElement).getName() : qName;
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

  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (!(obj instanceof CompletionElement)) return false;

    Object thatObj = ((CompletionElement)obj).myEqualityObject;
    if (myEqualityObject instanceof MethodSignature) {
      return thatObj instanceof MethodSignature &&
             MethodSignatureUtil.METHOD_PARAMETERS_ERASURE_EQUALITY.equals((MethodSignature)myEqualityObject, (MethodSignature)thatObj);
    }
    return Comparing.equal(myEqualityObject, thatObj);
  }

  @Override
  public int hashCode() {
    if (myEqualityObject instanceof MethodSignature) {
      return MethodSignatureUtil.METHOD_PARAMETERS_ERASURE_EQUALITY.computeHashCode((MethodSignature)myEqualityObject);
    }
    return myEqualityObject != null ? myEqualityObject.hashCode() : 0;
  }

  public boolean isMoreSpecificThan(@NotNull CompletionElement prev) {
    Object prevElement = prev.getElement();
    if (!(prevElement instanceof PsiMethod && myElement instanceof PsiMethod)) return false;

    PsiType prevType = prev.getSubstitutor().substitute(((PsiMethod)prevElement).getReturnType());
    PsiType candidateType = mySubstitutor.substitute(((PsiMethod)myElement).getReturnType());
    return prevType != null && candidateType != null && !prevType.equals(candidateType) && prevType.isAssignableFrom(candidateType);
  }

}
