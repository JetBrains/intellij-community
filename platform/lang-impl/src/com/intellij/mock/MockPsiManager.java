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
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.PsiModificationTrackerImpl;
import com.intellij.psi.impl.PsiTreeChangeEventImpl;
import com.intellij.psi.impl.file.impl.FileManager;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.ThrowableRunnable;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class MockPsiManager extends PsiManagerEx {
  private final Project myProject;
  private final Map<VirtualFile,PsiDirectory> myDirectories = new THashMap<VirtualFile, PsiDirectory>();
  private MockFileManager myMockFileManager;
  private PsiModificationTrackerImpl myPsiModificationTracker;
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
  public PsiModificationTracker getModificationTracker() {
    if (myPsiModificationTracker == null) {
      myPsiModificationTracker = new PsiModificationTrackerImpl(myProject);
    }
    return myPsiModificationTracker;
  }

  public void startBatchFilesProcessingMode() {
  }

  public void finishBatchFilesProcessingMode() {
  }

  public <T> T getUserData(@NotNull Key<T> key) {
    return null;
  }

  public <T> void putUserData(@NotNull Key<T> key, T value) {
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

  @Override
  public void dropFileCaches(@NotNull PsiFile file) {
  }

  public boolean isBatchFilesProcessingMode() {
    return false;
  }

  public boolean isAssertOnFileLoading(@NotNull VirtualFile file) {
    return false;
  }

  @Override
  public void beforeChange(boolean isPhysical) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void afterChange(boolean isPhysical) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  public ResolveCache getResolveCache() {
    if (myResolveCache == null) {
      myResolveCache = new ResolveCache(getProject().getMessageBus());
    }
    return myResolveCache;
  }

  public void registerRunnableToRunOnChange(@NotNull Runnable runnable) {
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

  public void beforeChildRemoval(@NotNull final PsiTreeChangeEventImpl event) {
  }

  @Override
  public void beforeChildReplacement(@NotNull final PsiTreeChangeEventImpl event) {
  }

  @Override
  public void beforeChildAddition(@NotNull PsiTreeChangeEventImpl event) {
  }
}
