// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.file.impl;

import com.intellij.codeInsight.multiverse.CodeInsightContext;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import com.intellij.util.concurrency.annotations.RequiresWriteLock;
import org.jetbrains.annotations.*;

import java.util.List;
import java.util.function.Consumer;

@ApiStatus.Internal
public interface FileManagerEx extends FileManager {
  void forEachCachedDocument(@NotNull Consumer<? super @NotNull Document> consumer);

  @TestOnly
  void assertNoInjectedFragmentsStoredInMaps();

  /**
   * Updates the context of `viewProvider` to `context` if the current context of viewProvider is anyContext.
   * If the current context of viewProvider is not anyContext, does nothing.
   *
   * @return the effective context of viewProvider, or `null` if viewProvider is missing in the cache.
   */
  @Nullable CodeInsightContext trySetContext(@NotNull FileViewProvider viewProvider, @NotNull CodeInsightContext context);

  void removeFilesAndDirsRecursively(@NotNull VirtualFile vFile);

  @Nullable
  PsiFile getCachedPsiFileInner(@NotNull VirtualFile file, @NotNull CodeInsightContext context);

  @NotNull @Unmodifiable
  List<PsiFile> getCachedPsiFilesInner(@NotNull VirtualFile file);

  /**
   * Removes invalid files and directories from the cache.
   *
   * @param useFind pass {@code true} if it's expected that file view providers might have changed.
   *                In this case, all files will be checked more thoroughly.
   */
  @RequiresWriteLock
  void removeInvalidFilesAndDirs(boolean useFind);

  void reloadPsiAfterTextChange(@NotNull FileViewProvider viewProvider, @NotNull VirtualFile vFile);

  @RequiresReadLock(generateAssertion = false)
  boolean evaluateValidity(@NotNull PsiFile file);

  @Nullable PsiFile getRawCachedFile(@NotNull VirtualFile vFile, @NotNull CodeInsightContext context);

  void forceReload(@NotNull VirtualFile vFile);

  void firePropertyChangedForUnloadedPsi();

  void dispose();

  void processQueue();

  @RequiresReadLock
  PsiFile getFastCachedPsiFile(@NotNull VirtualFile vFile, @NotNull CodeInsightContext context);

  void processFileTypesChanged(boolean clearViewProviders);

  @RequiresWriteLock
  void possiblyInvalidatePhysicalPsi();

  void dispatchPendingEvents();

  @TestOnly
  void checkConsistency();

  PsiDirectory getCachedDirectory(@NotNull VirtualFile vFile);
}