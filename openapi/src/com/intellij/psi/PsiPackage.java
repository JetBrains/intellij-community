/*
 * Copyright 2000-2005 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi;

import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.IncorrectOperationException;

public interface PsiPackage extends PsiElement, PsiNamedElement, NavigationItem {
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