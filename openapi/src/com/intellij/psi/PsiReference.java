/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

import com.intellij.openapi.util.TextRange;
import com.intellij.util.IncorrectOperationException;

public interface PsiReference {
  PsiReference[] EMPTY_ARRAY = new PsiReference[0];

  PsiElement getElement();

  /**
   * Relative range in element
   * @return
   */
  TextRange getRangeInElement();

  PsiElement resolve();
  String getCanonicalText();

  PsiElement handleElementRename(String newElementName) throws IncorrectOperationException;
  PsiElement bindToElement(PsiElement element) throws IncorrectOperationException;

  boolean isReferenceTo(PsiElement element);
  Object[] getVariants();

  boolean isSoft();
}