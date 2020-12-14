// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs;

import com.intellij.openapi.vfs.*;
import org.jetbrains.annotations.NotNull;

public class VirtualFileFilteringListener implements VirtualFileListener {
  private final VirtualFileListener myDelegate;
  @NotNull
  private final VirtualFileSystem myFileSystem;

  public VirtualFileFilteringListener(@NotNull VirtualFileListener delegate, @NotNull VirtualFileSystem fileSystem) {
    myDelegate = delegate;
    myFileSystem = fileSystem;
  }

  private boolean isFromMySystem(@NotNull VirtualFileEvent event) {
    return event.getFile().getFileSystem() == myFileSystem;
  }

  @Override
  public void beforeContentsChange(@NotNull VirtualFileEvent event) {
    if (isFromMySystem(event)) {
      myDelegate.beforeContentsChange(event);
    }
  }

  @Override
  public void beforeFileDeletion(@NotNull VirtualFileEvent event) {
    if (isFromMySystem(event)) {
      myDelegate.beforeFileDeletion(event);
    }
  }

  @Override
  public void beforeFileMovement(@NotNull VirtualFileMoveEvent event) {
    if (isFromMySystem(event)) {
      myDelegate.beforeFileMovement(event);
    }
  }

  @Override
  public void beforePropertyChange(@NotNull VirtualFilePropertyEvent event) {
    if (isFromMySystem(event)) {
      myDelegate.beforePropertyChange(event);
    }
  }

  @Override
  public void contentsChanged(@NotNull VirtualFileEvent event) {
    if (isFromMySystem(event)) {
      myDelegate.contentsChanged(event);
    }
  }

  @Override
  public void fileCopied(@NotNull VirtualFileCopyEvent event) {
    if (isFromMySystem(event)) {
      myDelegate.fileCopied(event);
    }
  }

  @Override
  public void fileCreated(@NotNull VirtualFileEvent event) {
    if (isFromMySystem(event)) {
      myDelegate.fileCreated(event);
    }
  }

  @Override
  public void fileDeleted(@NotNull VirtualFileEvent event) {
    if (isFromMySystem(event)) {
      myDelegate.fileDeleted(event);
    }
  }

  @Override
  public void fileMoved(@NotNull VirtualFileMoveEvent event) {
    if (isFromMySystem(event)) {
      myDelegate.fileMoved(event);
    }
  }

  @Override
  public void propertyChanged(@NotNull VirtualFilePropertyEvent event) {
    if (isFromMySystem(event)) {
      myDelegate.propertyChanged(event);
    }
  }
}
