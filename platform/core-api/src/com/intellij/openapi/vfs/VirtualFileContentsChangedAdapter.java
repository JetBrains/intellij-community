// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs;

import org.jetbrains.annotations.NotNull;

public abstract class VirtualFileContentsChangedAdapter implements VirtualFileListener {
  @Override
  public void contentsChanged(@NotNull VirtualFileEvent event) {
    onFileChange(event.getFile());
  }

  @Override
  public void fileCreated(@NotNull VirtualFileEvent event) {
    onFileChange(event.getFile());
  }

  @Override
  public void beforeFileDeletion(@NotNull VirtualFileEvent event) {
    onBeforeFileChange(event.getFile());
  }

  @Override
  public void beforeFileMovement(@NotNull VirtualFileMoveEvent event) {
    onBeforeFileChange(event.getFile());
  }

  @Override
  public void fileMoved(@NotNull VirtualFileMoveEvent event) {
    onFileChange(event.getFile());
  }

  @Override
  public void fileCopied(@NotNull VirtualFileCopyEvent event) {
    onFileChange(event.getFile());
  }

  protected abstract void onFileChange(final @NotNull VirtualFile fileOrDirectory);
  protected abstract void onBeforeFileChange(final @NotNull VirtualFile fileOrDirectory);

  @Override
  public void fileDeleted(@NotNull VirtualFileEvent event) {
    onFileChange(event.getFile());
  }

  @Override
  public void beforeContentsChange(@NotNull VirtualFileEvent event) {
    onBeforeFileChange(event.getFile());
  }
}
