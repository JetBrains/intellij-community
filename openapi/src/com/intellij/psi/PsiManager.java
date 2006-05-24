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

import com.intellij.lang.Language;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.javadoc.JavadocManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The main entry point for accessing the PSI services for a project.
 */
public abstract class PsiManager implements UserDataHolder {
  /**
   * Returns the PSI manager instance for the specified project.
   *
   * @param project the project for which the PSI manager is requested.
   * @return the PSI manager instance.
   */
  public static @NotNull PsiManager getInstance(@NotNull Project project) {
    return project.getComponent(PsiManager.class);
  }

  /**
   * Returns the project with which the PSI manager is associated.
   *
   * @return the project instance.
   */
  public abstract @NotNull Project getProject();

  /**
   * @deprecated
   */
  public abstract @NotNull PsiDirectory[] getRootDirectories(int rootType);

  /**
   * Returns the PSI file corresponding to the specified virtual file.
   *
   * @param file the file for which the PSI is requested.
   * @return the PSI file, or null if there is no PSI for the specified file in this project.
   */
  public abstract @Nullable PsiFile findFile(@NotNull VirtualFile file);

  public abstract @Nullable FileViewProvider findViewProvider(@NotNull VirtualFile file);

  /**
   * Returns the PSI directory corresponding to the specified virtual file system directory.
   *
   * @param file the directory for which the PSI is requested.
   * @return the PSI directory, or null if there is no PSI for the specified directory in this project.
   */
  public abstract @Nullable PsiDirectory findDirectory(@NotNull VirtualFile file);

  /**
   * Searches the project and all its libraries for a class with the specified full-qualified
   * name and returns one if it is found.
   *
   * @param qualifiedName the full-qualified name of the class to find.
   * @return the PSI class, or null if no class with such name is found.
   * @deprecated use {@link #findClass(String, GlobalSearchScope)}
   */
  public abstract @Nullable PsiClass findClass(@NotNull @NonNls String qualifiedName);

  /**
   * Searches the specified scope within the project for a class with the specified full-qualified
   * name and returns one if it is found.
   *
   * @param qualifiedName the full-qualified name of the class to find.
   * @param scope the scope to search.
   * @return the PSI class, or null if no class with such name is found.
   */
  public abstract @Nullable PsiClass findClass(@NonNls @NotNull String qualifiedName, @NotNull GlobalSearchScope scope);

  /**
   * Searches the specified scope within the project for classes with the specified full-qualified
   * name and returns all found classes.
   *
   * @param qualifiedName the full-qualified name of the class to find.
   * @param scope the scope to search.
   * @return the array of found classes, or an empty array if no classes are found.
   */
  public abstract @NotNull PsiClass[] findClasses(@NonNls @NotNull String qualifiedName, @NotNull GlobalSearchScope scope);

  /**
   * Searches the project for the package with the specified full-qualified name and retunrs one
   * if it is found.
   *
   * @param qualifiedName the full-qualified name of the package to find.
   * @return the PSI package, or null if no package with such name is found.
   */
  public abstract @Nullable PsiPackage findPackage(@NonNls @NotNull String qualifiedName);

  /**
   * Checks if the specified two PSI elements (possibly invalid) represent the same source element
   * (for example, a class with the same full-qualified name). Can be used to match two versions of the
   * PSI tree with each other after a reparse.
   *
   * @param element1 the first element to check for equivalence
   * @param element2 the second element to check for equivalence
   * @return true if the elements are equivalent, false if the elements are different or
   * it was not possible to determine the equivalence
   */
  public abstract boolean areElementsEquivalent(@Nullable PsiElement element1, @Nullable PsiElement element2);

  /**
   * Reloads the contents of the specified PSI file and its associated document (if any) from the disk.
   * @param file the PSI file to reload.
   */
  public abstract void reloadFromDisk(@NotNull PsiFile file);   //todo: move to FileDocumentManager

  /**
   * Adds a listener for receiving notifications about all changes in the PSI tree of the project.
   *
   * @param listener the listener instance.
   */
  public abstract void addPsiTreeChangeListener(@NotNull PsiTreeChangeListener listener);

  /**
   * Removes a listener for receiving notifications about all changes in the PSI tree of the project.
   *
   * @param listener the listener instance.
   */
  public abstract void removePsiTreeChangeListener(@NotNull PsiTreeChangeListener listener);

  /**
   * Returns the code style manager for the project. The code style manager can be used
   * to reformat code fragments, get names for elements according to the user's code style
   * and work with import statements and full-qualified names.
   *
   * @return the code style manager instance.
   */
  public abstract @NotNull CodeStyleManager getCodeStyleManager();

  /**
   * Returns the element factory for the project, which can be used to
   * create instances of Java and XML PSI elements.
   *
   * @return the element factory instance.
   */
  public abstract @NotNull PsiElementFactory getElementFactory();

  /**
   * Returns the search helper for the project, which provides low-level search and
   * find usages functionality. It can be used to perform operations like finding references
   * to an element, finding overriding / inheriting elements, finding to do items and so on.
   *
   * @return the search helper instance.
   */
  public abstract @NotNull PsiSearchHelper getSearchHelper();

  /**
   * Returns the resolve helper for the project, which can be used to resolve references
   * and check accessibility of elements.
   *
   * @return the resolve helper instance.
   */
  public abstract @NotNull PsiResolveHelper getResolveHelper();

  /**
   * Returns the short name cache for the project, which can be used to locate files, classes,
   * methods and fields by non-qualified names.
   *
   * @return the short name cache instance.
   */
  public abstract @NotNull PsiShortNamesCache getShortNamesCache();

  /**
   * Registers a custom short name cache implementation for the project, which is used
   * in addition to the standard IDEA implementation. Should not be used by most plugins.
   *
   * @param cache the short name cache instance.
   */
  public abstract void registerShortNamesCache(@NotNull PsiShortNamesCache cache);

  /**
   * Initiates a migrate refactoring. The refactoring is finished when
   * {@link com.intellij.psi.PsiMigration#finish()} is called.
   *
   * @return the migrate operation object.
   */
  public abstract @NotNull PsiMigration startMigration();

  /**
   * Returns the JavaDoc manager for the project, which can be used to retrieve
   * information about JavaDoc tags known to IDEA.
   *
   * @return the JavaDoc manager instance.
   */
  public abstract @NotNull JavadocManager getJavadocManager();

  /**
   * Returns the name helper for the project, which can be used to validate
   * and parse Java identifiers.
   *
   * @return the name helper instance.
   */
  public abstract @NotNull PsiNameHelper getNameHelper();

  /**
   * Returns the constant expression evaluator for the project.
   *
   * @return the evaluator instance.
   */
  public abstract @NotNull PsiConstantEvaluationHelper getConstantEvaluationHelper();

  /**
   * Returns the modification tracker for the project, which can be used to get the PSI
   * modification count value.
   *
   * @return the modification tracker instance.
   */
  public abstract @NotNull PsiModificationTracker getModificationTracker();


  /**
   * Returns the cached values manager for the project, which can be used to create values
   * which are automatically recalculated based on changes of the elements on which they depend.
   *
   * @return the cached values manager instance.
   */
  public abstract @NotNull CachedValuesManager getCachedValuesManager();

  /**
   * Moves the specified file to the specified directory.
   *
   * @param file         the file to move.
   * @param newParentDir the directory to move the file into.
   * @throws IncorrectOperationException if the modification is not supported or not possible for some reason.
   */
  public abstract void moveFile(@NotNull PsiFile file, @NotNull PsiDirectory newParentDir) throws IncorrectOperationException;

  /**
   * Moves the specified directory to the specified parent directory.
   *
   * @param dir          the directory to move.
   * @param newParentDir the directory to move <code>dir</code> into.
   * @throws IncorrectOperationException if the modification is not supported or not possible for some reason.
   */
  public abstract void moveDirectory(@NotNull PsiDirectory dir, @NotNull PsiDirectory newParentDir) throws IncorrectOperationException;

  /**
   * Checks if it is possible to move the specified PSI element under the specified container,
   * and throws an exception if the move is not possible. Does not actually modify anything.
   *
   * @param element      the element to check the move possibility.
   * @param newContainer the target container element to move into.
   * @throws IncorrectOperationException if the modification is not supported or not possible for some reason.
   */
  public abstract void checkMove(@NotNull PsiElement element, @NotNull PsiElement newContainer) throws IncorrectOperationException;

  /**
   * Notifies the PSI manager that a batch operation sequentially processing multiple files
   * is starting. Memory occupied by cached PSI trees is released more eagerly during such a
   * batch operation.
   */
  public abstract void startBatchFilesProcessingMode();

  /**
   * Notifies the PSI manager that a batch operation sequentially processing multiple files
   * is finishing. Memory occupied by cached PSI trees is released more eagerly during such a
   * batch operation.
   */
  public abstract void finishBatchFilesProcessingMode();

  /**
   * Checks if the PSI manager has been disposed and the PSI for this project can no
   * longer be used.
   *
   * @return true if the PSI manager is disposed, false otherwise.
   */
  public abstract boolean isDisposed();

  /**
   * Returns the language level set for this project.
   *
   * @deprecated  use {@link com.intellij.psi.PsiJavaFile#getLanguageLevel()} or
   * {@link com.intellij.psi.util.PsiUtil#getLanguageLevel(PsiElement)}
   * @return the language level instance.
   */
  public abstract @NotNull LanguageLevel getEffectiveLanguageLevel();

  /**
   * Checks if the specified package name is part of the package prefix for
   * any of the modules in this project.
   *
   * @param packageName the package name to check.
   * @return true if it is part of the package prefix, false otherwise. 
   */
  public abstract boolean isPartOfPackagePrefix(String packageName);

  /**
   * Sets the language level to use for this project. For tests only.
   *
   * @param languageLevel the language level to set.
   */
  public abstract void setEffectiveLanguageLevel(@NotNull LanguageLevel languageLevel);

  /**
   * Clears the resolve caches of the PSI manager. Can be used to reduce memory consumption
   * in batch operations sequentially processing multiple files.
   */
  public abstract void dropResolveCaches();

  /**
   * Checks if the specified PSI element belongs to the specified package.
   *
   * @param element  the element to check the package for.
   * @param aPackage the package to check.
   * @return true if the element belongs to the package, false otherwise.
   */
  public abstract boolean isInPackage(@NotNull PsiElement element, @NotNull PsiPackage aPackage);

  /**
   * Checks if the specified PSI elements belong to the same package.
   *
   * @param element1 the first element to check.
   * @param element2 the second element to check.
   * @return true if the elements are in the same package, false otherwise.
   */
  public abstract boolean arePackagesTheSame(@NotNull PsiElement element1, @NotNull PsiElement element2);

  /**
   * Checks if the specified PSI element belongs to the sources of the project.
   *
   * @param element the element to check.
   * @return true if the element belongs to the sources of the project, false otherwise.
   */
  public abstract boolean isInProject(@NotNull PsiElement element);

  /**
   * Disables automatic formatting of modified PSI elements, runs the specified operation
   * and re-enables the formatting. Can be used to improve performance of PSI write
   * operations.
   *
   * @param r the operation to run.
   */
  public abstract void performActionWithFormatterDisabled(Runnable r);
  public abstract <T> T performActionWithFormatterDisabled(Computable<T> r);

  public abstract void postponeAutoFormattingInside(Runnable runnable);

  public abstract void registerLanguageInjector(@NotNull LanguageInjector injector);

  public abstract void unregisterLanguageInjector(@NotNull LanguageInjector injector);

  @Nullable
  public abstract List<Pair<Language,TextRange>> getInjectedLanguages(PsiLanguageInjectionHost host);
}
