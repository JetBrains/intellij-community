/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolder;
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
import org.jetbrains.NotNull;
import org.jetbrains.Nullable;

public abstract class PsiManager implements UserDataHolder {
  public static @NotNull PsiManager getInstance(@NotNull Project project) {
    return project.getComponent(PsiManager.class);
  }

  public abstract @NotNull Project getProject();

  /**
   * @deprecated
   */
  public abstract @NotNull PsiDirectory[] getRootDirectories(int rootType);

  public abstract @Nullable PsiFile findFile(@NotNull VirtualFile file);

  public abstract @Nullable PsiDirectory findDirectory(@NotNull VirtualFile file);

  /**
   * @deprecated use {@link #findClass(String, GlobalSearchScope)}
   */
  public abstract @Nullable PsiClass findClass(@NotNull String qualifiedName);

  public abstract @Nullable PsiClass findClass(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope);
  public abstract @NotNull PsiClass[] findClasses(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope);

  public abstract @Nullable PsiPackage findPackage(@NotNull String qualifiedName);

  public abstract boolean areElementsEquivalent(@Nullable PsiElement element1, @Nullable PsiElement element2);

  //todo move to FileDocumentManager
  public abstract void reloadFromDisk(@NotNull PsiFile file);

  public abstract void addPsiTreeChangeListener(@NotNull PsiTreeChangeListener listener);

  public abstract void removePsiTreeChangeListener(@NotNull PsiTreeChangeListener listener);

  public abstract @NotNull CodeStyleManager getCodeStyleManager();

  public abstract @NotNull PsiElementFactory getElementFactory();

  public abstract @NotNull PsiSearchHelper getSearchHelper();

  public abstract @NotNull PsiResolveHelper getResolveHelper();

  public abstract @NotNull PsiShortNamesCache getShortNamesCache();

  public abstract void registerShortNamesCache(@NotNull PsiShortNamesCache cache);

  public abstract @NotNull PsiMigration startMigration();

  public abstract @NotNull JavadocManager getJavadocManager();

  public abstract @NotNull PsiNameHelper getNameHelper();

  public abstract @NotNull PsiConstantEvaluationHelper getConstantEvaluationHelper();

  public abstract @NotNull PsiModificationTracker getModificationTracker();

  public abstract @NotNull PsiAspectManager getAspectManager();

  public abstract @NotNull CachedValuesManager getCachedValuesManager();

  public abstract void moveFile(@NotNull PsiFile file, @NotNull PsiDirectory newParentDir) throws IncorrectOperationException;

  public abstract void moveDirectory(@NotNull PsiDirectory dir, @NotNull PsiDirectory newParentDir) throws IncorrectOperationException;

  public abstract void checkMove(@NotNull PsiElement element, @NotNull PsiElement newContainer) throws IncorrectOperationException;

  public abstract void startBatchFilesProcessingMode();

  public abstract void finishBatchFilesProcessingMode();

  public abstract boolean isDisposed();

  public abstract @NotNull LanguageLevel getEffectiveLanguageLevel();

  public abstract boolean isPartOfPackagePrefix(String packageName);

  /**
   * For tests only
   */
  public abstract void setEffectiveLanguageLevel(@NotNull LanguageLevel languageLevel);

  public abstract void dropResolveCaches();

  public abstract boolean isInPackage(@NotNull PsiElement element, @NotNull PsiPackage aPackage);

  public abstract boolean arePackagesTheSame(@NotNull PsiElement element1, @NotNull PsiElement element2);

  public abstract boolean isInProject(@NotNull PsiElement element);
}
