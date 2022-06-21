// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.SystemIndependent;

import java.io.File;
import java.nio.file.Path;

/**
 * To be used in async Project initialization tasks to add files silently,
 * preventing triggering of 'add new files to vcs' notifications/dialogs
 * by {@link com.intellij.openapi.vcs.VcsVFSListener}.
 *
 * @see GitRepositoryInitializer
 * @see com.intellij.openapi.vcs.VcsFileListenerContextHelper
 */
@ApiStatus.Internal
public interface GitSilentFileAdder {
  /**
   * Schedule file to be added into vcs silently
   * <p>
   * Method should be called before firing the corresponding VFS event.
   * Meaning, inside the same write command or before VFS refresh for externally changed files.
   */
  void markFileForAdding(@NotNull @SystemIndependent String path, boolean isDirectory);

  void markFileForAdding(@NotNull VirtualFile file);

  void markFileForAdding(@NotNull File file, boolean isDirectory);

  void markFileForAdding(@NotNull Path path, boolean isDirectory);

  /**
   * Notify that marked files can be added on pooled thread.
   * <p>
   * Should be called after explicit VFS refresh if files are added externally (ex: using NIO).
   */
  void finish();

  class Empty implements GitSilentFileAdder {
    @Override
    public void markFileForAdding(@NotNull @SystemIndependent String path, boolean isDirectory) {
    }

    @Override
    public void markFileForAdding(@NotNull VirtualFile file) {
    }

    @Override
    public void markFileForAdding(@NotNull File file, boolean isDirectory) {
    }

    @Override
    public void markFileForAdding(@NotNull Path path, boolean isDirectory) {
    }

    @Override
    public void finish() {
    }
  }
}
