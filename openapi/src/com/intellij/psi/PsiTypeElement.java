/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

public interface PsiTypeElement extends PsiElement {
  PsiTypeElement[] EMPTY_ARRAY = new PsiTypeElement[0];
  PsiType getType();
  PsiJavaCodeReferenceElement getInnermostComponentReferenceElement();
}
