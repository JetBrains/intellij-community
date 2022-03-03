// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.psi.impl.file.impl;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.PsiManagerEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.List;

/**
 * @see PsiManagerEx#getFileManager()
 */
public interface FileManager {
  @Nullable
  PsiFile findFile(@NotNull VirtualFile vFile);

  @Nullable
  PsiDirectory findDirectory(@NotNull VirtualFile vFile);

  void reloadFromDisk(@NotNull PsiFile file); //Q: move to PsiFile(Impl)?

  @Nullable
  PsiFile getCachedPsiFile(@NotNull VirtualFile vFile);

  @TestOnly
  void cleanupForNextTest();

  FileViewProvider findViewProvider(@NotNull VirtualFile file);
  FileViewProvider findCachedViewProvider(@NotNull VirtualFile file);
  void setViewProvider(@NotNull VirtualFile virtualFile, @Nullable FileViewProvider fileViewProvider);

  @NotNull
  List<PsiFile> getAllCachedFiles();

  @NotNull
  FileViewProvider createFileViewProvider(@NotNull VirtualFile file, boolean eventSystemEnabled);
}
