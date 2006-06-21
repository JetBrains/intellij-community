package com.intellij.mock;

import com.intellij.lang.Language;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.PsiModificationTrackerImpl;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.javadoc.JavadocManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class MockPsiManager extends PsiManager {
  public Project getProject() {
    return null;
  }

  public PsiDirectory[] getRootDirectories(int rootType) {
    return PsiDirectory.EMPTY_ARRAY;
  }

  public OrderEntry findOrderEntry(PsiElement element) {
    return null;  //To change body of implemented methods use Options | File Templates.
  }

  public PsiFile findFile(VirtualFile file) {
    return null;
  }

  public
  @Nullable
  FileViewProvider findViewProvider(@NotNull VirtualFile file) {
    return null;
  }

  public @Nullable PsiFile findFile(@NotNull VirtualFile file, @NotNull Language aspect) {
    return null;
  }

  public @NotNull Language[] getKnownAspects(@NotNull VirtualFile file) {
    return new Language[0];
  }

  public PsiDirectory findDirectory(VirtualFile file) {
    return null;
  }

  public PsiClass findClass(String qualifiedName) {
    return null;
  }

  public PsiClass findClass(String qualifiedName, GlobalSearchScope scope) {
    return null;
  }

  public PsiClass[] findClasses(String qualifiedName, GlobalSearchScope scope) {
    return PsiClass.EMPTY_ARRAY;
  }

  public PsiPackage findPackage(String qualifiedName) {
    return null;
  }

  public boolean areElementsEquivalent(PsiElement element1, PsiElement element2) {
    return false;
  }

  public LanguageLevel getEffectiveLanguageLevel() {
    return LanguageLevel.HIGHEST;
  }

  public boolean isPartOfPackagePrefix(String packageName) {
    return false;
  }


  public PsiMigration startMigration() {
    return null;
  }

  public void commit(PsiFile file) {
  }

  public void commitAll() {
  }

  public PsiFile[] getAllFilesToCommit() {
    return PsiFile.EMPTY_ARRAY;
  }

  public void reloadFromDisk(PsiFile file) {
  }

  public void addPsiTreeChangeListener(PsiTreeChangeListener listener) {
  }

  public void addPsiTreeChangeListener(@NotNull PsiTreeChangeListener listener, Disposable parentDisposable) {
  }

  public void removePsiTreeChangeListener(PsiTreeChangeListener listener) {
  }

  public CodeStyleManager getCodeStyleManager() {
    return null;
  }

  public PsiElementFactory getElementFactory() {
    return null;
  }

  public PsiSearchHelper getSearchHelper() {
    return null;
  }

  public PsiResolveHelper getResolveHelper() {
    return null;
  }

  public PsiShortNamesCache getShortNamesCache() {
    return null;
  }

  public void registerShortNamesCache(PsiShortNamesCache cache) {
  }

  public JavadocManager getJavadocManager() {
    return null;
  }

  public PsiNameHelper getNameHelper() {
    return null;
  }

  //public PsiConstantEvaluationHelper getConstantEvaluationHelper() {
  //  return new PsiConstantEvaluationHelperImpl();
  //}

  public PsiModificationTracker getModificationTracker() {
    return new PsiModificationTrackerImpl(this);
  }

  public CachedValuesManager getCachedValuesManager() {
    return null;
  }

  public void moveFile(PsiFile file, PsiDirectory newParentDir) throws IncorrectOperationException {
  }

  public void moveDirectory(PsiDirectory dir, PsiDirectory newParentDir) throws IncorrectOperationException {
  }

  public void checkMove(PsiElement element, PsiElement newContainer) throws IncorrectOperationException {
  }

  public void startBatchFilesProcessingMode() {
  }

  public void finishBatchFilesProcessingMode() {
  }

  public <T> T getUserData(Key<T> key) {
    return null;
  }

  public <T> void putUserData(Key<T> key, T value) {
  }

  public boolean isDisposed() {
    return false;
  }


  public void setEffectiveLanguageLevel(LanguageLevel languageLevel) {
  }

  public void dropResolveCaches() {
  }

  public boolean isInPackage(PsiElement element, PsiPackage aPackage) {
    return false;
  }

  public boolean arePackagesTheSame(PsiElement element1, PsiElement element2) {
    return false;
  }

  public boolean isInProject(PsiElement element) {
    return false;
  }

  public void performActionWithFormatterDisabled(Runnable r) {
    r.run();
  }

  public <T> T performActionWithFormatterDisabled(Computable<T> r) {
    return r.compute();
  }

  public void registerLanguageInjector(LanguageInjector injector) {
  }

  public void registerLanguageInjector(@NotNull LanguageInjector injector, Disposable parentDisposable) {
  }

  public void unregisterLanguageInjector(@NotNull LanguageInjector injector) {

  }

  public void postponeAutoFormattingInside(Runnable runnable) {
    PostprocessReformattingAspect.getInstance(getProject()).postponeFormattingInside(runnable);
  }

  public List<LanguageInjector> getLanguageInjectors() {
    return Collections.emptyList();
  }

  public PsiConstantEvaluationHelper getConstantEvaluationHelper() {
    return null;
  }
}
