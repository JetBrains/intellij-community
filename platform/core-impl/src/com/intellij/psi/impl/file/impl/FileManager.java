// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi.impl.file.impl;

import com.intellij.codeInsight.multiverse.CodeInsightContext;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import com.intellij.util.concurrency.annotations.RequiresWriteLock;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.List;

/**
 * @see PsiManagerEx#getFileManager()
 */
public interface FileManager {
  @RequiresReadLock
  @Nullable PsiFile findFile(@NotNull VirtualFile vFile);

  /**
   * @deprecated this method is a temporary solution, don't use it explicitly unless you consulted with Maksim Medvedev
   */
  @Deprecated
  @ApiStatus.Internal
  @RequiresReadLock
  @Nullable
  PsiFile findFile(@NotNull VirtualFile vFile, @NotNull CodeInsightContext context);

  @RequiresReadLock
  @Nullable
  PsiDirectory findDirectory(@NotNull VirtualFile vFile);

  @RequiresWriteLock
  void reloadFromDisk(@NotNull PsiFile psiFile); //Q: move to PsiFile(Impl)?

  @RequiresReadLock
  @Nullable
  PsiFile getCachedPsiFile(@NotNull VirtualFile vFile);

  /**
   * @deprecated this method is a temporary solution, don't use it explicitly unless you consulted with Maksim Medvedev
   * see IJPL-339
   */
  @Deprecated
  @ApiStatus.Internal
  @Nullable
  PsiFile getCachedPsiFile(@NotNull VirtualFile vFile, @NotNull CodeInsightContext context);

  @TestOnly
  void cleanupForNextTest();

  FileViewProvider findViewProvider(@NotNull VirtualFile vFile);

  /**
   * @deprecated this method is a temporary solution, don't use it explicitly unless you consulted with Maksim Medvedev
   */
  @Deprecated
  @ApiStatus.Internal
  FileViewProvider findViewProvider(@NotNull VirtualFile vFile, @NotNull CodeInsightContext context);

  @Nullable FileViewProvider findCachedViewProvider(@NotNull VirtualFile vFile);

  @ApiStatus.Internal
  @NotNull List<@NotNull FileViewProvider> findCachedViewProviders(@NotNull VirtualFile vFile);

  /**
   * @deprecated this method is a temporary solution, don't use it explicitly unless you consulted with Maksim Medvedev
   */
  @Deprecated
  @ApiStatus.Internal
  @Nullable
  FileViewProvider findCachedViewProvider(@NotNull VirtualFile vFile, @NotNull CodeInsightContext context);

  /**
   * Requires write lock for physical files, and <i>usually</i> does not require a write lock for non-physical files.
   */
  void setViewProvider(@NotNull VirtualFile vFile, @Nullable FileViewProvider viewProvider);

  @NotNull
  List<PsiFile> getAllCachedFiles();

  @NotNull
  FileViewProvider createFileViewProvider(@NotNull VirtualFile vFile, boolean eventSystemEnabled);

  /**
   * @deprecated this method is a temporary solution, don't use it explicitly unless you consulted with Maksim Medvedev
   */
  @Deprecated
  @ApiStatus.Internal
  @NotNull
  FileViewProvider createFileViewProvider(@NotNull VirtualFile vFile, @NotNull CodeInsightContext context, boolean eventSystemEnabled);
}
