// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

/**
 * {@link com.intellij.openapi.vfs.VirtualFileSystem} implementation for local files, that doesn't co-operate with
 * {@link com.intellij.openapi.vfs.newvfs.ManagingFS} -- i.e. doesn't use caching and refreshing mechanisms.
 * 2 main use-cases:
 * <ol>
 *   <li>
 *     VFS outside IDE: e.g. kotlin compiler, or some other service, that uses JB API, which often requires VFS -- but doesn't
 *     want to instantiate whole {@link com.intellij.openapi.vfs.newvfs.ManagingFS} machinery
 *   </li>
 *   <li>
 *     VFS inside IDE, before {@link com.intellij.openapi.vfs.newvfs.ManagingFS} is initialized -- i.e. to open config files
 *     early during IDE startup while {@link com.intellij.openapi.vfs.newvfs.ManagingFS} is not yet initialized.
 *   </li>
 * </ol>
 */
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
