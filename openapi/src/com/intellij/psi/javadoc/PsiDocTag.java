/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.javadoc;


import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;

public interface PsiDocTag extends PsiElement, PsiNamedElement{
  PsiDocTag[] EMPTY_ARRAY = new PsiDocTag[0];

  PsiDocComment getContainingComment();
  PsiElement getNameElement();
  String getName();
  PsiElement[] getDataElements();
  PsiDocTagValue getValueElement();
}