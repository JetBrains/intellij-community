/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

public interface PsiImportStatement extends PsiImportStatementBase {
  PsiImportStatement[] EMPTY_ARRAY = new PsiImportStatement[0];
  String getQualifiedName();
}
