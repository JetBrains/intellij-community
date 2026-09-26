// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class IndexedFileWrapper implements FileContent {
  private final @NotNull IndexedFile myFile;

  public IndexedFileWrapper(@NotNull IndexedFile file) {
    myFile = file;
  }

  public @NotNull IndexedFile getIndexedFile() {
    return myFile;
  }

  @Override
  public byte @NotNull [] getContent() {
    throw new UnsupportedOperationException();
  }

  @Override
  public @NotNull CharSequence getContentAsText() {
    throw new UnsupportedOperationException();
  }

  @Override
  public @NotNull PsiFile getPsiFile() {
    throw new UnsupportedOperationException();
  }

  @Override
  public @NotNull FileType getFileType() {
    return myFile.getFileType();
  }

  @Override
  public @NotNull VirtualFile getFile() {
    return myFile.getFile();
  }

  @Override
  public @NotNull String getFileName() {
    return myFile.getFileName();
  }

  @Override
  public Project getProject() {
    return myFile.getProject();
  }

  @Override
  public @Nullable <T> T getUserData(@NotNull Key<T> key) {
    return myFile.getUserData(key);
  }

  @Override
  public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
    myFile.putUserData(key, value);
  }
}
