/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

import com.intellij.util.IncorrectOperationException;

/**
 *
 */
public interface PsiIdentifier extends PsiJavaToken {
  PsiElement replace(PsiElement newElement) throws IncorrectOperationException;
}
