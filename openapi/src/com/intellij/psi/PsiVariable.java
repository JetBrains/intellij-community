/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

import com.intellij.util.IncorrectOperationException;

public interface PsiVariable extends PsiElement, PsiModifierListOwner, PsiNamedElement {
  PsiType getType();

  PsiTypeElement getTypeElement();

  PsiExpression getInitializer();
  boolean hasInitializer();

  // Q: split into normalizeBrackets and splitting declarations?
  void normalizeDeclaration() throws IncorrectOperationException;

  Object computeConstantValue();

  PsiIdentifier getNameIdentifier();
}
