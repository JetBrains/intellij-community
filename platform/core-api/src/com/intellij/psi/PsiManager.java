// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.codeInsight.multiverse.CodeInsightContext;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The main entry point for accessing the PSI services for a project.
 */
public abstract class PsiManager extends UserDataHolderBase {
  /**
   * Returns the PSI manager instance for the specified project.
   *
   * @param project the project for which the PSI manager is requested.
   * @return the PSI manager instance.
   */
  public static @NotNull PsiManager getInstance(@NotNull Project project) {
    return project.getService(PsiManager.class);
  }

  /**
   * Returns the project with which the PSI manager is associated.
   *
   * @return the project instance
   */
  public abstract @NotNull Project getProject();

  /**
   * Returns the PSI file corresponding to the specified virtual file.
   *
   * @param file the file for which the PSI is requested.
   * @return the PSI file, or {@code null} if {@code file} is a directory, an invalid virtual file,
   * or the current project is a dummy or default project.
   */
  @RequiresReadLock
  @RequiresBackgroundThread(generateAssertion = false)
  public abstract @Nullable PsiFile findFile(@NotNull VirtualFile file);


  @ApiStatus.Experimental
  @RequiresReadLock
  @RequiresBackgroundThread(generateAssertion = false)
  public abstract @Nullable PsiFile findFile(@NotNull VirtualFile file, @NotNull CodeInsightContext context);

  public abstract @Nullable FileViewProvider findViewProvider(@NotNull VirtualFile file);

  @ApiStatus.Experimental
  @RequiresReadLock
  @RequiresBackgroundThread(generateAssertion = false)
  public abstract @Nullable FileViewProvider findViewProvider(@NotNull VirtualFile file, @NotNull CodeInsightContext context);

  /**
   * Returns the PSI directory corresponding to the specified virtual file system directory.
   *
   * @param file the directory for which the PSI is requested.
   * @return the PSI directory, or {@code null} if there is no PSI for the specified directory in this project.
   */
  @RequiresBackgroundThread(generateAssertion = false)
  public abstract @Nullable PsiDirectory findDirectory(@NotNull VirtualFile file);

  /**
   * Checks if the specified two PSI elements (possibly invalid) represent the same source element
   * or can be considered equivalent for resolve purposes.
   * <p>
   * Can be used to match two versions of the PSI tree with each other after a reparse.
   * <p>
   * For example, Java classes with the same fully qualified name are equivalent, which is useful when working
   * with both library source and class roots. Source and compiled classes are definitely different ({@code equals()} returns {@code false}),
   * but for reference resolve or inheritance checks, they're equivalent.
   *
   * @param element1 the first element to check for equivalence
   * @param element2 the second element to check for equivalence
   * @return {@code true} if the elements are equivalent, {@code false} if the elements are different,
   * or it was not possible to determine the equivalence
   */
  public abstract boolean areElementsEquivalent(@Nullable PsiElement element1, @Nullable PsiElement element2);

  /**
   * Reloads the contents of the specified PSI file and its associated document (if any) from the disk.
   *
   * @param psiFile the PSI file to reload.
   */
  public abstract void reloadFromDisk(@NotNull PsiFile psiFile);

  /**
   * Adds a listener for receiving notifications about all changes in the PSI tree of the project.
   *
   * @param listener the listener instance
   * @deprecated Please use the overload with specified parent disposable
   */
  @Deprecated
  public abstract void addPsiTreeChangeListener(@NotNull PsiTreeChangeListener listener);

  /**
   * Adds a listener for receiving notifications about all changes in the PSI tree of the project.
   *
   * @param listener         the listener instance
   * @param parentDisposable object, after whose disposing the listener should be removed
   */
  public abstract void addPsiTreeChangeListener(@NotNull PsiTreeChangeListener listener, @NotNull Disposable parentDisposable);

  /**
   * Removes a listener for receiving notifications about all changes in the PSI tree of the project.
   *
   * @param listener the listener instance
   */
  public abstract void removePsiTreeChangeListener(@NotNull PsiTreeChangeListener listener);

  /**
   * Returns the modification tracker for the project, which can be used to get the PSI
   * modification count value.
   *
   * @return the modification tracker instance.
   */
  public abstract @NotNull PsiModificationTracker getModificationTracker();

  /**
   * Notifies the PSI manager that a batch operation sequentially processing multiple files
   * is starting. Memory occupied by cached PSI trees is released more eagerly during such a
   * batch operation.
   * @deprecated Use {@link #runInBatchFilesMode(Computable)}
   */
  @Deprecated
  public abstract void startBatchFilesProcessingMode();

  /**
   * Notifies the PSI manager that a batch operation sequentially processing multiple files
   * is finishing. Memory occupied by cached PSI trees is released more eagerly during such a
   * batch operation.
   * @deprecated Use {@link #runInBatchFilesMode(Computable)}
   */
  @Deprecated
  public abstract void finishBatchFilesProcessingMode();

  /**
   * Notifies the PSI manager that the batch operation {@code runnable}
   * (which usually sequentially processes multiple files)
   * is started and perform this operation.
   * Memory occupied by cached PSI trees is released more eagerly during batch operations.
   */
  public abstract <T> T runInBatchFilesMode(@NotNull Computable<T> runnable);

  /**
   * Checks if the PSI manager has been disposed, and the PSI for this project can no longer be used.
   *
   * @return {@code true} if the PSI manager is disposed, {@code false} otherwise.
   */
  public abstract boolean isDisposed();

  /**
   * Clears the resolve caches of the PSI manager. Can be used to reduce memory consumption
   * in batch operations sequentially processing multiple files. Can be invoked from any thread.
   */
  public abstract void dropResolveCaches();

  /**
   * Clears all {@link com.intellij.psi.util.CachedValue} depending on {@link PsiModificationTracker#MODIFICATION_COUNT} and resolve caches.
   * Can be used to reduce memory consumption in batch operations sequentially processing multiple files.
   */
  @RequiresEdt
  public abstract void dropPsiCaches();

  /**
   * Checks if the specified PSI element belongs to the sources of the project.
   *
   * @param element the element to check.
   * @return {@code true} if the element belongs to the sources of the project, {@code false} otherwise.
   */
  public abstract boolean isInProject(@NotNull PsiElement element);

  // todo ijpl-339 do we need context here?
  @ApiStatus.Internal
  public abstract @Nullable FileViewProvider findCachedViewProvider(@NotNull VirtualFile vFile);
}
