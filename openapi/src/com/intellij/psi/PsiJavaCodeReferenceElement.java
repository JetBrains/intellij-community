/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface PsiJavaCodeReferenceElement extends PsiElement, PsiJavaReference {
  PsiJavaCodeReferenceElement[] EMPTY_ARRAY = new PsiJavaCodeReferenceElement[0];

  @Nullable
  PsiElement getReferenceNameElement();

  @Nullable
  PsiReferenceParameterList getParameterList();

  @NotNull
  PsiType[] getTypeParameters();

  @Nullable
  PsiElement getQualifier();
  boolean isQualified();

  String getQualifiedName();

  @Nullable
  String getReferenceName();
}
