/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

import com.intellij.pom.java.PomMethod;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;

import java.util.List;

public interface PsiMethod extends PsiMember, PsiNamedElement, PsiModifierListOwner, PsiDocCommentOwner, PsiTypeParameterListOwner {
  PsiMethod[] EMPTY_ARRAY = new PsiMethod[0];

  PsiType getReturnType();

  PsiTypeElement getReturnTypeElement();

  PsiParameterList getParameterList();

  PsiReferenceList getThrowsList();

  PsiCodeBlock getBody();

  boolean isConstructor();

  boolean isVarArgs();

  MethodSignature getSignature(PsiSubstitutor substitutor);

  PsiIdentifier getNameIdentifier();

  PsiMethod[] findSuperMethods();

  PsiMethod[] findSuperMethods(boolean checkAccess);

  PsiMethod[] findSuperMethods(PsiClass parentClass);

  List<MethodSignatureBackedByPsiMethod> findSuperMethodSignaturesIncludingStatic(boolean checkAccess);

  PsiMethod findConstructorInSuper();

  PsiMethod findDeepestSuperMethod();

  PomMethod getPom();
}
