/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

/**
 * @author dsl
 */
public interface PsiMigration {
  PsiClass createClass(String qualifiedName);
  PsiPackage createPackage(String qualifiedName);
  void finish();
}
