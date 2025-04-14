// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.scope;

import com.intellij.codeInsight.completion.CompletionUtilCoreImpl;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Trinity;
import com.intellij.psi.*;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public final class CompletionElement{
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

  public @NotNull String getQualifierText() {
    return myQualifierText;
  }

  public PsiSubstitutor getSubstitutor(){
    return mySubstitutor;
  }

  public Object getElement(){
    return myElement;
  }

  private @Nullable Object getUniqueId(){
    if(myElement instanceof PsiClass){
      String qName = ((PsiClass)myElement).getQualifiedName();
      return qName == null ? ((PsiClass)myElement).getName() : qName;
    }
    if(myElement instanceof PsiPackage){
      return ((PsiPackage)myElement).getQualifiedName();
    }
    if(myElement instanceof PsiMethod){
      if (myMethodRefType != null) {
        return ((PsiMethod)myElement).isConstructor() ? JavaKeywords.NEW : ((PsiMethod)myElement).getName();
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
             MethodSignatureUtil.areSignaturesErasureEqual((MethodSignature)myEqualityObject, (MethodSignature)thatObj);
    }
    return Comparing.equal(myEqualityObject, thatObj);
  }

  @Override
  public int hashCode() {
    if (myEqualityObject instanceof MethodSignature) {
      return myEqualityObject.hashCode();
    }
    return myEqualityObject != null ? myEqualityObject.hashCode() : 0;
  }

  @ApiStatus.Internal
  public @Nullable PsiType getMethodRefType() {
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

    PsiType prevType =
      GenericsUtil.getVariableTypeByExpressionType(another.getSubstitutor().substitute(((PsiMethod)anotherElement).getReturnType()));
    PsiType candidateType = GenericsUtil.getVariableTypeByExpressionType(mySubstitutor.substitute(((PsiMethod)myElement).getReturnType()));
    return prevType != null && candidateType != null && !prevType.equals(candidateType) &&
           prevType.isAssignableFrom(candidateType);
  }

}
