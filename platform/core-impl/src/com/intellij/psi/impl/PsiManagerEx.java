// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.file.impl.FileManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

/**
 * @author peter
 */
public abstract class PsiManagerEx extends PsiManager {
  public static PsiManagerEx getInstanceEx(Project project) {
    //noinspection SSBasedInspection
    return (PsiManagerEx)getInstance(project);
  }

  public abstract boolean isBatchFilesProcessingMode();

  @TestOnly
  public abstract void setAssertOnFileLoadingFilter(@NotNull VirtualFileFilter filter, @NotNull Disposable parentDisposable);

  public abstract boolean isAssertOnFileLoading(@NotNull VirtualFile file);

  /**
   * @param runnable to be run before <b>physical</b> PSI change
   * @deprecated subscribe to {@link PsiManagerImpl#ANY_PSI_CHANGE_TOPIC} directly with proper {@link Disposable} on connection
   */
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.1")
  @Deprecated
  public abstract void registerRunnableToRunOnChange(@NotNull Runnable runnable);

  /**
   * @param runnable to be run before <b>physical</b> or <b>non-physical</b> PSI change
   * @deprecated subscribe to {@link PsiManagerImpl#ANY_PSI_CHANGE_TOPIC} directly with proper {@link Disposable} on connection
   */
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.1")
  @Deprecated
  public abstract void registerRunnableToRunOnAnyChange(@NotNull Runnable runnable);

  /**
   * @deprecated subscribe to {@link PsiManagerImpl#ANY_PSI_CHANGE_TOPIC} directly with proper {@link Disposable} on connection
   */
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.1")
  @Deprecated
  public abstract void registerRunnableToRunAfterAnyChange(@NotNull Runnable runnable);

  @NotNull
  public abstract FileManager getFileManager();

  public abstract void beforeChildAddition(@NotNull PsiTreeChangeEventImpl event);

  public abstract void beforeChildRemoval(@NotNull PsiTreeChangeEventImpl event);

  public abstract void beforeChildReplacement(@NotNull PsiTreeChangeEventImpl event);

  public abstract void beforeChange(boolean isPhysical);

  public abstract void afterChange(boolean isPhysical);
}
