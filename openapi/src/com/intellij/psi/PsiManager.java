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
import com.intellij.psi.jsp.JspElementFactory;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.IncorrectOperationException;

public abstract class PsiManager implements UserDataHolder {
  public static PsiManager getInstance(Project project) {
    return project.getComponent(PsiManager.class);
  }

  public abstract Project getProject();

  /**
   * @deprecated
   */
  public abstract PsiDirectory[] getRootDirectories(int rootType);

  public abstract PsiFile findFile(VirtualFile file);

  public abstract PsiDirectory findDirectory(VirtualFile file);

  /**
   * @deprecated use {@link #findClass(String, GlobalSearchScope)}
   */
  public abstract PsiClass findClass(String qualifiedName);

  public abstract PsiClass findClass(String qualifiedName, GlobalSearchScope scope);
  public abstract PsiClass[] findClasses(String qualifiedName, GlobalSearchScope scope);

  public abstract PsiPackage findPackage(String qualifiedName);

  public abstract boolean areElementsEquivalent(PsiElement element1, PsiElement element2);

  //todo move to FileDocumentManager
  public abstract void reloadFromDisk(PsiFile file);

  public abstract void addPsiTreeChangeListener(PsiTreeChangeListener listener);

  public abstract void removePsiTreeChangeListener(PsiTreeChangeListener listener);

  public abstract CodeStyleManager getCodeStyleManager();

  public abstract PsiElementFactory getElementFactory();

  public abstract JspElementFactory getJspElementFactory();

  public abstract PsiSearchHelper getSearchHelper();

  public abstract PsiResolveHelper getResolveHelper();

  public abstract PsiShortNamesCache getShortNamesCache();

  public abstract void registerShortNamesCache(PsiShortNamesCache cache);

  public abstract PsiMigration startMigration();

  public abstract JavadocManager getJavadocManager();

  public abstract PsiNameHelper getNameHelper();

  public abstract PsiConstantEvaluationHelper getConstantEvaluationHelper();

  public abstract PsiModificationTracker getModificationTracker();

  public abstract PsiAspectManager getAspectManager();

  public abstract CachedValuesManager getCachedValuesManager();

  public abstract void moveFile(PsiFile file, PsiDirectory newParentDir) throws IncorrectOperationException;

  public abstract void moveDirectory(PsiDirectory dir, PsiDirectory newParentDir) throws IncorrectOperationException;

  public abstract void checkMove(PsiElement element, PsiElement newContainer) throws IncorrectOperationException;

  public abstract void startBatchFilesProcessingMode();

  public abstract void finishBatchFilesProcessingMode();

  public abstract boolean isDisposed();

  public abstract LanguageLevel getEffectiveLanguageLevel();

  public abstract boolean isPartOfPackagePrefix(String packageName);

  /**
   * For tests only
   */
  public abstract void setEffectiveLanguageLevel(LanguageLevel languageLevel);



  public abstract void dropResolveCaches();

  public abstract boolean isInPackage(PsiElement element, PsiPackage aPackage);

  public abstract boolean arePackagesTheSame(PsiElement element1, PsiElement element2);

  public abstract boolean isInProject(PsiElement element);
}
