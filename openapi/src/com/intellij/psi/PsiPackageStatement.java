/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

/**
 *
 */
public interface PsiPackageStatement extends PsiElement {
  PsiJavaCodeReferenceElement getPackageReference();
  String getPackageName();
  PsiModifierList getAnnotationList();
}
