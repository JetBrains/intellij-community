// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.mock;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.PsiModificationTrackerImpl;
import com.intellij.psi.impl.PsiTreeChangeEventImpl;
import com.intellij.psi.impl.file.impl.FileManager;
import com.intellij.psi.util.PsiModificationTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public final /* not final for Android Studio tests */ class MockPsiManager extends PsiManagerEx {
  private final Project myProject;
  private final Map<VirtualFile,PsiDirectory> myDirectories = new HashMap<>();
  private MockFileManager myMockFileManager;
  private PsiModificationTrackerImpl myPsiModificationTracker;

  public MockPsiManager(@NotNull Project project) {
    myProject = project;
  }

  public void addPsiDirectory(VirtualFile file, PsiDirectory psiDirectory) {
    myDirectories.put(file, psiDirectory);
  }

  @Override
  public @NotNull Project getProject() {
    return myProject;
  }

  @Override
  public PsiFile findFile(@NotNull VirtualFile file) {
    return null;
  }

  @Override
  public @Nullable
  FileViewProvider findViewProvider(@NotNull VirtualFile file) {
    return null;
  }

  @Override
  public PsiDirectory findDirectory(@NotNull VirtualFile file) {
    return myDirectories.get(file);
  }

  @Override
  public boolean areElementsEquivalent(PsiElement element1, PsiElement element2) {
    return Comparing.equal(element1, element2);
  }

  @Override
  public void reloadFromDisk(@NotNull PsiFile file) {
  }

  @Override
  public void addPsiTreeChangeListener(@NotNull PsiTreeChangeListener listener) {
  }

  @Override
  public void addPsiTreeChangeListener(@NotNull PsiTreeChangeListener listener, @NotNull Disposable parentDisposable) {
  }

  @Override
  public void removePsiTreeChangeListener(@NotNull PsiTreeChangeListener listener) {
  }

  @Override
  public @NotNull PsiModificationTracker getModificationTracker() {
    if (myPsiModificationTracker == null) {
      myPsiModificationTracker = new PsiModificationTrackerImpl(myProject);
    }
    return myPsiModificationTracker;
  }

  @Override
  public void startBatchFilesProcessingMode() {
  }

  @Override
  public void finishBatchFilesProcessingMode() {
  }

  @Override
  public <T> T runInBatchFilesMode(@NotNull Computable<T> runnable) {
    return null;
  }

  @Override
  public <T> T getUserData(@NotNull Key<T> key) {
    return null;
  }

  @Override
  public <T> void putUserData(@NotNull Key<T> key, T value) {
  }

  @Override
  public boolean isDisposed() {
    return false;
  }

  @Override
  public void dropResolveCaches() {
    getFileManager().cleanupForNextTest();
  }

  @Override
  public void dropPsiCaches() {
    dropResolveCaches();
  }

  @Override
  public boolean isInProject(@NotNull PsiElement element) {
    return false;
  }

  @Override
  public @Nullable FileViewProvider findCachedViewProvider(@NotNull VirtualFile vFile) {
    return null;
  }

  @Override
  public boolean isBatchFilesProcessingMode() {
    return false;
  }

  @Override
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

  @Override
  public @NotNull FileManager getFileManager() {
    if (myMockFileManager == null) {
      myMockFileManager = new MockFileManager(this);
    }
    return myMockFileManager;
  }

  @Override
  public void beforeChildRemoval(final @NotNull PsiTreeChangeEventImpl event) {
  }

  @Override
  public void beforeChildReplacement(final @NotNull PsiTreeChangeEventImpl event) {
  }

  @Override
  public void beforeChildAddition(@NotNull PsiTreeChangeEventImpl event) {
  }

  @Override
  public void setAssertOnFileLoadingFilter(@NotNull VirtualFileFilter filter, @NotNull Disposable parentDisposable) {

  }
}
