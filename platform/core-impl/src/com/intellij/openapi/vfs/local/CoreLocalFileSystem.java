// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.local;

import com.intellij.openapi.vfs.DeprecatedVirtualFileSystem;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;

public class CoreLocalFileSystem extends DeprecatedVirtualFileSystem {
  @Override
  public @NotNull String getProtocol() {
    return StandardFileSystems.FILE_PROTOCOL;
  }

  public @Nullable VirtualFile findFileByIoFile(@NotNull File file) {
    return findFileByNioFile(file.toPath());
  }

  public @Nullable VirtualFile findFileByNioFile(@NotNull Path file) {
    return Files.exists(file) ? new CoreLocalVirtualFile(this, file) : null;
  }

  @Override
  public VirtualFile findFileByPath(@NotNull String path) {
    return findFileByNioFile(FileSystems.getDefault().getPath(path));
  }

  @Override
  public void refresh(boolean asynchronous) { }

  @Override
  public VirtualFile refreshAndFindFileByPath(@NotNull String path) {
    return findFileByPath(path);
  }

  @Override
  public @Nullable Path getNioPath(@NotNull VirtualFile file) {
    return file.getFileSystem() == this && file instanceof CoreLocalVirtualFile ? ((CoreLocalVirtualFile)file).getFile() : null;
  }
}
