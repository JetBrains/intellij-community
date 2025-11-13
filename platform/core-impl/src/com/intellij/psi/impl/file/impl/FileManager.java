// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi.impl.file.impl;

import com.intellij.codeInsight.multiverse.CodeInsightContext;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import com.intellij.util.concurrency.annotations.RequiresWriteLock;
import org.jetbrains.annotations.*;

import java.util.List;

/**
 * @see PsiManagerEx#getFileManager()
 */
public interface FileManager {
  // todo IJPL-339 mark deprecated?
  @RequiresReadLock
  @Nullable PsiFile findFile(@NotNull VirtualFile vFile);

  @ApiStatus.Experimental
  @RequiresReadLock
  @Nullable
  PsiFile findFile(@NotNull VirtualFile vFile, @NotNull CodeInsightContext context);

  @RequiresReadLock
  @Nullable
  PsiDirectory findDirectory(@NotNull VirtualFile vFile);

  @RequiresWriteLock
  void reloadFromDisk(@NotNull PsiFile psiFile); //Q: move to PsiFile(Impl)?

  // todo IJPL-339 mark deprecated?
  @RequiresReadLock
  @Nullable
  PsiFile getCachedPsiFile(@NotNull VirtualFile vFile);

  /**
   * @return list of cached PSI files. Note that the list can be shorter than {@link #findCachedViewProviders(VirtualFile)} because
   * not all view providers have cached PSI files.
   */
  @ApiStatus.Experimental
  @RequiresReadLock
  @NotNull @Unmodifiable
  List<@NotNull PsiFile> getCachedPsiFiles(@NotNull VirtualFile vFile);

  @ApiStatus.Experimental
  @Nullable
  PsiFile getCachedPsiFile(@NotNull VirtualFile vFile, @NotNull CodeInsightContext context);

  @TestOnly
  void cleanupForNextTest();

  // todo IJPL-339 mark deprecated?
  @NotNull FileViewProvider findViewProvider(@NotNull VirtualFile vFile);

  @ApiStatus.Experimental
  FileViewProvider findViewProvider(@NotNull VirtualFile vFile, @NotNull CodeInsightContext context);

  // todo IJPL-339 mark deprecated?
  @Nullable FileViewProvider findCachedViewProvider(@NotNull VirtualFile vFile);

  @ApiStatus.Experimental
  @NotNull @Unmodifiable
  List<@NotNull FileViewProvider> findCachedViewProviders(@NotNull VirtualFile vFile);

  @ApiStatus.Experimental
  @Nullable
  FileViewProvider findCachedViewProvider(@NotNull VirtualFile vFile, @NotNull CodeInsightContext context);

  /** @deprecated use {@link #changeViewProvider(VirtualFile, FileViewProvider)} or {@link #dropViewProviders(VirtualFile)} instead. */
  @Deprecated
  void setViewProvider(@NotNull VirtualFile vFile, @Nullable FileViewProvider viewProvider);

  /**
   * Requires write lock for physical files, and <i>usually</i> does not require a write lock for non-physical files.
   * <p>
   * If a file has several view providers, all of them will be invalidated.
   */
  @ApiStatus.Experimental
  void changeViewProvider(@NotNull VirtualFile vFile, @NotNull FileViewProvider viewProvider);

  /**
   * Requires write lock for physical files, and <i>usually</i> does not require a write lock for non-physical files.
   */
  @ApiStatus.Experimental
  void dropViewProviders(@NotNull VirtualFile vFile);

  @NotNull
  List<PsiFile> getAllCachedFiles();

  // todo IJPL-339 mark deprecated?
  @NotNull
  FileViewProvider createFileViewProvider(@NotNull VirtualFile vFile, boolean eventSystemEnabled);

  @ApiStatus.Experimental
  @NotNull
  FileViewProvider createFileViewProvider(@NotNull VirtualFile vFile, @NotNull CodeInsightContext context, boolean eventSystemEnabled);
}
