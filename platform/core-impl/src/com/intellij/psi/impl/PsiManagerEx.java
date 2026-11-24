// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.file.impl.FileManager;
import com.intellij.psi.impl.file.impl.FileManagerEx;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

public abstract class PsiManagerEx extends PsiManager {
  @TestOnly
  @ApiStatus.Internal
  public abstract void cleanupForNextTest();

  @ApiStatus.Internal
  public abstract void dropResolveCacheRegularly(@NotNull ProgressIndicator indicator);

  public static PsiManagerEx getInstanceEx(Project project) {
    //noinspection SSBasedInspection
    return (PsiManagerEx)getInstance(project);
  }

  public abstract boolean isBatchFilesProcessingMode();

  @TestOnly
  public abstract void setAssertOnFileLoadingFilter(@NotNull VirtualFileFilter filter, @NotNull Disposable parentDisposable);

  public abstract boolean isAssertOnFileLoading(@NotNull VirtualFile file);

  public abstract @NotNull FileManager getFileManager();

  @ApiStatus.Internal
  public abstract @NotNull FileManagerEx getFileManagerEx();

  public abstract void beforeChildAddition(@NotNull PsiTreeChangeEventImpl event);

  public abstract void beforeChildRemoval(@NotNull PsiTreeChangeEventImpl event);

  public abstract void beforeChildReplacement(@NotNull PsiTreeChangeEventImpl event);
  
  @ApiStatus.Internal
  public abstract void beforeChildrenChange(@NotNull PsiTreeChangeEventImpl event);

  @ApiStatus.Internal
  public abstract void beforeChildMovement(@NotNull PsiTreeChangeEventImpl event);

  @ApiStatus.Internal
  public abstract void beforePropertyChange(@NotNull PsiTreeChangeEventImpl event);

  @ApiStatus.Internal
  public abstract void childAdded(@NotNull PsiTreeChangeEventImpl event);

  @ApiStatus.Internal
  public abstract void childRemoved(@NotNull PsiTreeChangeEventImpl event);

  @ApiStatus.Internal
  public abstract void childReplaced(@NotNull PsiTreeChangeEventImpl event);

  @ApiStatus.Internal
  public abstract void childMoved(@NotNull PsiTreeChangeEventImpl event);

  @ApiStatus.Internal
  public abstract void childrenChanged(@NotNull PsiTreeChangeEventImpl event);

  @ApiStatus.Internal
  public abstract void propertyChanged(@NotNull PsiTreeChangeEventImpl event);

  @ApiStatus.Internal
  public abstract void addTreeChangePreprocessor(@NotNull PsiTreeChangePreprocessor preprocessor, @NotNull Disposable parentDisposable);

  /**
   * @deprecated use {@link #addTreeChangePreprocessor(PsiTreeChangePreprocessor, Disposable)} instead
   */
  @ApiStatus.Internal
  public abstract void addTreeChangePreprocessor(@NotNull PsiTreeChangePreprocessor preprocessor);

  /**
   * @deprecated use {@link #addTreeChangePreprocessor(PsiTreeChangePreprocessor, Disposable)} instead
   */
  @ApiStatus.Internal
  @Deprecated
  public abstract void removeTreeChangePreprocessor(@NotNull PsiTreeChangePreprocessor preprocessor);

  @ApiStatus.Internal
  public abstract void addTreeChangePreprocessorBackgroundable(@NotNull PsiTreeChangePreprocessor preprocessor,
                                                               @NotNull Disposable parentDisposable);

  /**
   * Publishes the start of a PSI change on {@link AnyPsiChangeListenerBackgroundable#TOPIC} and on
   * {@link PsiManagerImpl#ANY_PSI_CHANGE_TOPIC}. These topics are read-only, so this method and {@link #afterChange} are the only way
   * to publish on them.
   * <p>
   * The backgroundable topic gets the event first, in place.
   * The legacy topic gets a physical change on the EDT when the thread holds the write lock. Otherwise, it gets the event in place.
   * <p>
   * To drop the resolve caches without a PSI change, call {@link #dropResolveCaches()}.
   *
   * @param isPhysical whether the change is in a physical PSI file
   */
  public abstract void beforeChange(boolean isPhysical);

  /**
   * Publishes the end of a PSI change on {@link PsiManagerImpl#ANY_PSI_CHANGE_TOPIC} and on
   * {@link AnyPsiChangeListenerBackgroundable#TOPIC}. The backgroundable topic gets the event last.
   * The threads are the same as for {@link #beforeChange}.
   *
   * @param isPhysical whether the change is in a physical PSI file
   */
  public abstract void afterChange(boolean isPhysical);
}
