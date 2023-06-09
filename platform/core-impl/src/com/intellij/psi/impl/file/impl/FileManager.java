// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.psi.impl.file.impl;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.List;

/**
 * @see PsiManagerEx#getFileManager()
 */
public interface FileManager {
  @Nullable
  @RequiresReadLock
  PsiFile findFile(@NotNull VirtualFile vFile);

  @Nullable
  PsiDirectory findDirectory(@NotNull VirtualFile vFile);

  void reloadFromDisk(@NotNull PsiFile psiFile); //Q: move to PsiFile(Impl)?

  @Nullable
  PsiFile getCachedPsiFile(@NotNull VirtualFile vFile);

  @TestOnly
  void cleanupForNextTest();

  FileViewProvider findViewProvider(@NotNull VirtualFile vFile);
  FileViewProvider findCachedViewProvider(@NotNull VirtualFile vFile);
  void setViewProvider(@NotNull VirtualFile vFile, @Nullable FileViewProvider viewProvider);

  @NotNull
  List<PsiFile> getAllCachedFiles();

  @NotNull
  FileViewProvider createFileViewProvider(@NotNull VirtualFile vFile, boolean eventSystemEnabled);
}
