/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

public interface PsiJavaCodeReferenceElement extends PsiElement, PsiJavaReference {
  PsiJavaCodeReferenceElement[] EMPTY_ARRAY = new PsiJavaCodeReferenceElement[0];

  PsiElement getReferenceNameElement();

  PsiReferenceParameterList getParameterList();

  PsiType[] getTypeParameters();

  PsiElement getQualifier();
  boolean isQualified();

  String getQualifiedName();
  String getReferenceName();
}
