/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

public interface PsiParameter extends PsiVariable {
  PsiParameter[] EMPTY_ARRAY = new PsiParameter[0];
  PsiElement getDeclarationScope();
  boolean isVarArgs();
}
