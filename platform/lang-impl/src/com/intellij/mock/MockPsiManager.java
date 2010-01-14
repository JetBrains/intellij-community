/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.mock;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.PsiCachedValuesFactory;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.PsiModificationTrackerImpl;
import com.intellij.psi.impl.PsiTreeChangeEventImpl;
import com.intellij.psi.impl.cache.CacheManager;
import com.intellij.psi.impl.cache.impl.CompositeCacheManager;
import com.intellij.psi.impl.file.impl.FileManager;
import com.intellij.psi.impl.search.PsiSearchHelperImpl;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.CachedValuesManagerImpl;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ThrowableRunnable;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class MockPsiManager extends PsiManagerEx {
  private final Project myProject;
  private final Map<VirtualFile,PsiDirectory> myDirectories = new THashMap<VirtualFile, PsiDirectory>();
  private CachedValuesManagerImpl myCachedValuesManager;
  private MockFileManager myMockFileManager;
  private PsiModificationTrackerImpl myPsiModificationTracker;
  private final CompositeCacheManager myCompositeCacheManager = new CompositeCacheManager();
  private ResolveCache myResolveCache;

  public MockPsiManager() {
    this(null);
  }

  public MockPsiManager(final Project project) {
    myProject = project;
  }

  public void addPsiDirectory(VirtualFile file, PsiDirectory psiDirectory) {
    myDirectories.put(file, psiDirectory);
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  public PsiFile findFile(@NotNull VirtualFile file) {
    return null;
  }
  
  @Nullable
  public
  FileViewProvider findViewProvider(@NotNull VirtualFile file) {
    return null;
  }

  public PsiDirectory findDirectory(@NotNull VirtualFile file) {
    return myDirectories.get(file);
  }

  public boolean areElementsEquivalent(PsiElement element1, PsiElement element2) {
    return Comparing.equal(element1, element2);
  }

  public void reloadFromDisk(@NotNull PsiFile file) {
  }

  public void addPsiTreeChangeListener(@NotNull PsiTreeChangeListener listener) {
  }

  public void addPsiTreeChangeListener(@NotNull PsiTreeChangeListener listener, Disposable parentDisposable) {
  }

  public void removePsiTreeChangeListener(@NotNull PsiTreeChangeListener listener) {
  }

  @NotNull
  public CodeStyleManager getCodeStyleManager() {
    return CodeStyleManager.getInstance(myProject);
  }

  @NotNull
  public PsiSearchHelper getSearchHelper() {
    return new PsiSearchHelperImpl(this);
  }

  @NotNull
  public PsiModificationTracker getModificationTracker() {
    if (myPsiModificationTracker == null) {
      myPsiModificationTracker = new PsiModificationTrackerImpl(myProject);
    }
    return myPsiModificationTracker;
  }

  @NotNull
  public CachedValuesManager getCachedValuesManager() {
    if (myCachedValuesManager == null) {
      myCachedValuesManager = new CachedValuesManagerImpl(myProject, new PsiCachedValuesFactory(this));
    }
    return myCachedValuesManager;
  }

  public void moveFile(@NotNull PsiFile file, @NotNull PsiDirectory newParentDir) throws IncorrectOperationException {
  }

  public void moveDirectory(@NotNull PsiDirectory dir, @NotNull PsiDirectory newParentDir) throws IncorrectOperationException {
  }

  public void checkMove(@NotNull PsiElement element, @NotNull PsiElement newContainer) throws IncorrectOperationException {
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

  public void dropResolveCaches() {
    getFileManager().cleanupForNextTest();
  }

  public boolean isInProject(@NotNull PsiElement element) {
    return false;
  }

  public void performActionWithFormatterDisabled(Runnable r) {
    r.run();
  }

  public <T extends Throwable> void performActionWithFormatterDisabled(ThrowableRunnable<T> r) throws T {
    r.run();
  }

  public <T> T performActionWithFormatterDisabled(Computable<T> r) {
    return r.compute();
  }

  public void registerLanguageInjector(@NotNull LanguageInjector injector) {
  }

  public void registerLanguageInjector(@NotNull LanguageInjector injector, Disposable parentDisposable) {
  }

  public void unregisterLanguageInjector(@NotNull LanguageInjector injector) {

  }

  public void postponeAutoFormattingInside(Runnable runnable) {
    PostprocessReformattingAspect.getInstance(getProject()).postponeFormattingInside(runnable);
  }

  @NotNull
  public List<LanguageInjector> getLanguageInjectors() {
    return Collections.emptyList();
  }

  public boolean isBatchFilesProcessingMode() {
    return false;
  }

  public boolean isAssertOnFileLoading(@NotNull VirtualFile file) {
    return false;
  }

  public void nonPhysicalChange() {
    throw new UnsupportedOperationException("Method nonPhysicalChange is not yet implemented in " + getClass().getName());
  }

  public void physicalChange() {
    throw new UnsupportedOperationException("physicalChange is not implemented"); // TODO
  }

  @NotNull
  public ResolveCache getResolveCache() {
    if (myResolveCache == null) {
      myResolveCache = new ResolveCache(this);
    }
    return myResolveCache;
  }

  public void registerRunnableToRunOnChange(@NotNull Runnable runnable) {
  }

  public void registerWeakRunnableToRunOnChange(@NotNull Runnable runnable) {
  }

  public void registerRunnableToRunOnAnyChange(@NotNull Runnable runnable) {
  }

  public void registerRunnableToRunAfterAnyChange(@NotNull Runnable runnable) {
    throw new UnsupportedOperationException("Method registerRunnableToRunAfterAnyChange is not yet implemented in " + getClass().getName());
  }

  @NotNull
  public FileManager getFileManager() {
    if (myMockFileManager == null) {
      myMockFileManager = new MockFileManager(this);
    }
    return myMockFileManager;
  }

  public void invalidateFile(@NotNull final PsiFile file) {
  }

  public void beforeChildRemoval(@NotNull final PsiTreeChangeEventImpl event) {
  }

  @NotNull
  public CacheManager getCacheManager() {
    return myCompositeCacheManager;
  }
}