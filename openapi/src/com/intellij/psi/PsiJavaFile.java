/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

public interface PsiJavaFile extends PsiFile{
  PsiClass[] getClasses();

  PsiPackageStatement getPackageStatement();
  String getPackageName();

  PsiImportList getImportList();
}