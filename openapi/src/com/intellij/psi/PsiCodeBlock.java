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
  PsiJavaToken getLBrace();
  PsiJavaToken getRBrace();
}
