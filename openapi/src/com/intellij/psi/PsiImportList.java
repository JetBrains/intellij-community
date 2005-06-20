/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

import org.jetbrains.annotations.NotNull;

/**
 *
 */
public interface PsiImportList extends PsiElement {
  @NotNull PsiImportStatement[] getImportStatements();
  @NotNull PsiImportStaticStatement[] getImportStaticStatements();
  @NotNull PsiImportStatementBase[] getAllImportStatements();
  PsiImportStatement findSingleClassImportStatement(String qName);
  PsiImportStatement findOnDemandImportStatement(String packageName);
  PsiImportStatementBase findSingleImportStatement(String name);
  boolean isReplaceEquivalent(PsiImportList otherList);
}
