/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

/**
 *
 */
public interface PsiCodeBlock extends PsiElement{
  PsiCodeBlock[] EMPTY_ARRAY = new PsiCodeBlock[0];
  PsiStatement[] getStatements();
  PsiElement getFirstBodyElement();
  PsiElement getLastBodyElement();

  /** can be null */
  PsiJavaToken getLBrace();

  /** can be null */
  PsiJavaToken getRBrace();

  boolean isEmpty();
}
