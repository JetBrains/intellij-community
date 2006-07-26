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

import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Allows to extend the mechanism of locating classes and packages by full-qualified name.
 * Implementations of this interface need to be registered as project components in order
 * to be picked up by {@link PsiManager}.
 */
public interface PsiElementFinder {
  /**
   * Searches the specified scope within the project for a class with the specified full-qualified
   * name and returns one if it is found.
   *
   * @param qualifiedName the full-qualified name of the class to find.
   * @param scope the scope to search.
   * @return the PSI class, or null if no class with such name is found.
   * @see PsiManager#findClass(String, com.intellij.psi.search.GlobalSearchScope)
   */
  @Nullable
  PsiClass findClass(@NotNull String qualifiedName, GlobalSearchScope scope);

  /**
   * Searches the specified scope within the project for classes with the specified full-qualified
   * name and returns all found classes.
   *
   * @param qualifiedName the full-qualified name of the class to find.
   * @param scope the scope to search.
   * @return the array of found classes, or an empty array if no classes are found.
   * @see PsiManager#findClasses(String, com.intellij.psi.search.GlobalSearchScope)
   */
  @NotNull
  PsiClass[] findClasses(String qualifiedName, GlobalSearchScope scope);

  /**
   * Searches the project for the package with the specified full-qualified name and retunrs one
   * if it is found.
   *
   * @param qualifiedName the full-qualified name of the package to find.
   * @return the PSI package, or null if no package with such name is found.
   * @see PsiManager#findPackage(String)
   */
  @Nullable
  PsiPackage findPackage(String qualifiedName);

  /**
   * Returns the list of subpackages of the specified package in the specified search scope.
   *
   * @param psiPackage the package to return the list of subpackages for.
   * @param scope the scope in which subpackages are searched.
   * @return the list of subpackages.
   * @see PsiPackage#getSubPackages(com.intellij.psi.search.GlobalSearchScope)
   */
  @NotNull
  PsiPackage[] getSubPackages(PsiPackage psiPackage, GlobalSearchScope scope);

  /**
   * Returns the list of classes in the specified package and in the specified search scope.
   *
   * @param psiPackage the package to return the list of classes in.
   * @param scope the scope in which classes are searched.
   * @return the list of classes.
   * @see PsiPackage#getClasses(com.intellij.psi.search.GlobalSearchScope)
   */
  @NotNull
  PsiClass[] getClasses(PsiPackage psiPackage, GlobalSearchScope scope);
}
