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
package com.intellij.psi.search;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * Allows to retrieve files and Java classes, methods and fields in a project by
 * non-qualified names.
 *
 * @see com.intellij.psi.PsiManager#getShortNamesCache()
 */
public interface PsiShortNamesCache {
  /**
   * Initializes the cache. To be used by custom PsiShortNameCache implementations
   * registered through {@link com.intellij.psi.PsiManager#registerShortNamesCache(PsiShortNamesCache)} }.
   */
  void runStartupActivity();

  /**
   * Returns the list of files with the specified name.
   *
   * @param name the name of the files to find.
   * @return the list of files in the project which have the specified name.
   */
  @NotNull
  PsiFile[] getFilesByName(@NotNull String name);

  /**
   * Returns the list of names of all files in the project.
   *
   * @return the list of all file names in the project.
   */
  @NotNull
  String[] getAllFileNames();

  /**
   * Returns the list of all classes with the specified name in the specified scope.
   *
   * @param name  the non-qualified name of the classes to find.
   * @param scope the scope in which classes are searched.
   * @return the list of found classes.
   */
  @NotNull
  PsiClass[] getClassesByName(@NotNull @NonNls String name, @NotNull GlobalSearchScope scope);

  /**
   * Returns the list of names of all classes in the project and
   * (optionally) libraries.
   *
   * @param searchInLibraries if true, libraries are included in the search;
   *                          otherwise, only the project is searched.
   * @return the list of all class names.
   */
  @NotNull
  String[] getAllClassNames(boolean searchInLibraries);

  /**
   * Adds the names of all classes in the project and (optionally) libraries
   * to the specified set.
   *
   * @param searchInLibraries if true, libraries are included in the search;
   *                          otherwise, only the project is searched.
   * @param dest              the set to add the names to.
   */
  void getAllClassNames(boolean searchInLibraries, @NotNull HashSet<String> dest);

  /**
   * Returns the list of all methods with the specified name in the specified scope.
   *
   * @param name  the name of the methods to find.
   * @param scope the scope in which methods are searched.
   * @return the list of found methods.
   */
  @NotNull
  PsiMethod[] getMethodsByName(@NonNls @NotNull String name, @NotNull GlobalSearchScope scope);

  @NotNull
  PsiMethod[] getMethodsByNameIfNotMoreThan(@NonNls @NotNull String name, @NotNull GlobalSearchScope scope, int maxCount);

  /**
   * Returns the list of names of all methods in the project and
   * (optionally) libraries.
   *
   * @param searchInLibraries if true, libraries are included in the search;
   *                          otherwise, only the project is searched.
   * @return the list of all method names.
   */
  @NotNull
  String[] getAllMethodNames(boolean searchInLibraries);

  /**
   * Adds the names of all methods in the project and (optionally) libraries
   * to the specified set.
   *
   * @param searchInLibraries if true, libraries are included in the search;
   *                          otherwise, only the project is searched.
   * @param set               the set to add the names to.
   */
  void getAllMethodNames(boolean searchInLibraries, @NotNull HashSet<String> set);

  /**
   * Returns the list of all fields with the specified name in the specified scope.
   *
   * @param name  the name of the fields to find.
   * @param scope the scope in which fields are searched.
   * @return the list of found fields.
   */
  @NotNull
  PsiField[] getFieldsByName(@NotNull @NonNls String name, @NotNull GlobalSearchScope scope);

  /**
   * Returns the list of names of all fields in the project and
   * (optionally) libraries.
   *
   * @param searchInLibraries if true, libraries are included in the search;
   *                          otherwise, only the project is searched.
   * @return the list of all field names.
   */
  @NotNull
  String[] getAllFieldNames(boolean searchInLibraries);

  /**
   * Adds the names of all methods in the project and (optionally) libraries
   * to the specified set.
   *
   * @param searchInLibraries if true, libraries are included in the search;
   *                          otherwise, only the project is searched.
   * @param set               the set to add the names to.
   */
  void getAllFieldNames(boolean searchInLibraries, @NotNull HashSet<String> set);
}
