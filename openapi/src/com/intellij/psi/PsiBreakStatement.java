/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

/**
 *
 */
public interface PsiBreakStatement extends PsiStatement {
  PsiIdentifier getLabelIdentifier();
  PsiStatement findExitedStatement();
}
