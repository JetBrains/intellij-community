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

import com.intellij.codeInsight.completion.CompletionUtilCoreImpl;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Trinity;
import com.intellij.psi.*;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public class CompletionElement{
  private final Object myElement;
  private final PsiSubstitutor mySubstitutor;
  private final Object myEqualityObject;
  private final String myQualifierText;
  private final @Nullable PsiType myMethodRefType;

  public CompletionElement(Object element, PsiSubstitutor substitutor) {
    this(element, substitutor, "", null);
  }

  CompletionElement(Object element, PsiSubstitutor substitutor, @NotNull String qualifierText, @Nullable PsiType methodRefType) {
    myElement = element;
    mySubstitutor = substitutor;
    myQualifierText = qualifierText;
    myMethodRefType = methodRefType;
    myEqualityObject = getUniqueId();
  }

  @NotNull
  public String getQualifierText() {
    return myQualifierText;
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
      if (myMethodRefType != null) {
        return ((PsiMethod)myElement).isConstructor() ? PsiKeyword.NEW : ((PsiMethod)myElement).getName();
      }

      return Trinity.create(((PsiMethod)myElement).getName(),
                            Arrays.asList(MethodSignatureUtil.calcErasedParameterTypes(((PsiMethod)myElement).getSignature(mySubstitutor))),
                            myQualifierText);
    }
    if (myElement instanceof PsiVariable) {
      return CompletionUtilCoreImpl.getOriginalOrSelf((PsiElement)myElement);
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

  @Nullable
  @ApiStatus.Internal
  public PsiType getMethodRefType() {
    return myMethodRefType;
  }

  public boolean isMoreSpecificThan(@NotNull CompletionElement another) {
    Object anotherElement = another.getElement();
    if (!(anotherElement instanceof PsiMethod && myElement instanceof PsiMethod)) return false;

    if (another.myMethodRefType instanceof PsiMethodReferenceType && myMethodRefType instanceof PsiClassType) {
      return true;
    }

    if (anotherElement != myElement &&
        ((PsiMethod)myElement).hasModifierProperty(PsiModifier.ABSTRACT) &&
        !((PsiMethod)anotherElement).hasModifierProperty(PsiModifier.ABSTRACT)) {
      return false;
    }

    PsiType prevType = TypeConversionUtil.erasure(another.getSubstitutor().substitute(((PsiMethod)anotherElement).getReturnType()));
    PsiType candidateType = mySubstitutor.substitute(((PsiMethod)myElement).getReturnType());
    return prevType != null && candidateType != null && !prevType.equals(candidateType) && prevType.isAssignableFrom(candidateType);
  }

}
