/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

import com.intellij.util.IncorrectOperationException;

public interface PsiModifierList extends PsiElement {
  boolean hasModifierProperty(String name);
  void setModifierProperty(String name, boolean value) throws IncorrectOperationException;
  void checkSetModifierProperty(String name, boolean value) throws IncorrectOperationException;

  PsiAnnotation[] getAnnotations();
  PsiAnnotation findAnnotation(String qualifiedName);
}
