/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.javadoc;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;

/**
 * @author mike
 */
public interface JavadocTagInfo {
  String getName();
  boolean isInline();
  boolean isValidInContext(PsiElement element);

  Object[] getPossibleValues(PsiElement context, PsiElement place, String prefix);

  /**
   * Checks the tag value for correctnes. Returns null if correct. Error message otherwise.
   */
  String checkTagValue(PsiDocTagValue value);

  PsiReference getReference(PsiDocTagValue value);
}
