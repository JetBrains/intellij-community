/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.IncorrectOperationException;

public interface PsiPackage extends PsiElement, PsiNamedElement {
  String getQualifiedName();

  PsiDirectory[] getDirectories();

  PsiDirectory[] getDirectories(GlobalSearchScope scope);

  PsiPackage getParentPackage();

  PsiPackage[] getSubPackages();

  PsiPackage[] getSubPackages(GlobalSearchScope scope);

  PsiClass[] getClasses();

  PsiClass[] getClasses(GlobalSearchScope scope);

  void checkSetName(String name) throws IncorrectOperationException;

  /**
   * This method must be invoked on the package after all directoris corresponding
   * to it have been renamed/moved accordingly to qualified name change.
   * @param newQualifiedName
   */
  void handleQualifiedNameChange(String newQualifiedName);

  /**
   * Returns source roots that this package occurs in package prefixes of.
   * @return
   */
  VirtualFile[] occursInPackagePrefixes();
}