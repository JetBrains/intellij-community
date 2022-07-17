// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
  @NotNull
  private final IndexedFile myFile;

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

  @NotNull
  @Override
  public CharSequence getContentAsText() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public PsiFile getPsiFile() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public FileType getFileType() {
    return myFile.getFileType();
  }

  @NotNull
  @Override
  public VirtualFile getFile() {
    return myFile.getFile();
  }

  @NotNull
  @Override
  public String getFileName() {
    return myFile.getFileName();
  }

  @Override
  public Project getProject() {
    return myFile.getProject();
  }

  @Nullable
  @Override
  public <T> T getUserData(@NotNull Key<T> key) {
    return myFile.getUserData(key);
  }

  @Override
  public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
    myFile.putUserData(key, value);
  }
}
