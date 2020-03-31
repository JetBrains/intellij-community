// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs;

import com.intellij.util.ThrowableConsumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

/**
 * Allows a version control plugin to intercept file operations performed in the local file system
 * and to perform them through the corresponding VCS.
 *
 * @author max
 * @see LocalFileSystem#registerAuxiliaryFileOperationsHandler(LocalFileOperationsHandler)
 */
public interface LocalFileOperationsHandler {
  /**
   * Intercepts the deletion of a file.
   * @param file the file being deleted.
   * @return true if the handler has performed the deletion, false if the deletion needs to be performed through
   * standard core logic.
   */
  boolean delete(@NotNull VirtualFile file) throws IOException;

  /**
   * Intercepts the movement of a file.
   * @param file  the file being moved.
   * @param toDir the destination directory.
   * @return true if the handler has performed the move, false if the move needs to be performed through
   * standard core logic.
   */
  boolean move(@NotNull VirtualFile file, @NotNull VirtualFile toDir) throws IOException;

  /**
   * Intercepts the copying of a file.
   * @param file  the file being copied.
   * @param toDir the destination directory.
   * @param copyName the name for the copy
   * @return the copy result if the handler has performed the copy, null if the copy needs to be performed through
   * standard core logic.
   */
  @Nullable
  File copy(@NotNull VirtualFile file, @NotNull VirtualFile toDir, @NotNull String copyName) throws IOException;

  /**
   * Intercepts the renaming of a file.
   * @param file  the file being renamed.
   * @param newName the new name.
   * @return true if the handler has performed the rename, false if the rename needs to be performed through
   * standard core logic.
   */
  boolean rename(@NotNull VirtualFile file, @NotNull String newName) throws IOException;

  /**
   * Intercepts the creation of a file.
   * @param dir  the directory in which the file is being created.
   * @param name the name of the new file.
   * @return true if the handler has performed the file creation, false if the creation needs to be performed through
   * standard core logic.
   */
  boolean createFile(@NotNull VirtualFile dir, @NotNull String name) throws IOException;

  /**
   * Intercepts the creation of a directory.
   * @param dir  the directory in which the directory is being created.
   * @param name the name of the new directory.
   * @return true if the handler has performed the directory creation, false if the creation needs to be performed through
   * standard core logic.
   */
  boolean createDirectory(@NotNull VirtualFile dir, @NotNull String name) throws IOException;

  void afterDone(@NotNull ThrowableConsumer<LocalFileOperationsHandler, IOException> invoker);
}
