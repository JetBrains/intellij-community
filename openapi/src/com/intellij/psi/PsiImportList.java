/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

/**
 *
 */
public interface PsiImportList extends PsiElement {
  PsiImportStatement[] getImportStatements();
  PsiImportStaticStatement[] getImportStaticStatements();
  PsiImportStatementBase[] getAllImportStatements();
  PsiImportStatement findSingleClassImportStatement(String qName);
  PsiImportStatement findOnDemandImportStatement(String packageName);
  PsiImportStatementBase findSingleImportStatement(String name);
}
