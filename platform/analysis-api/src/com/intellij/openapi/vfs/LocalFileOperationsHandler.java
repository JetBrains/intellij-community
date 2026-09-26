// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs;

import com.intellij.util.ThrowableConsumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
   * @return {@code true} if the handler has performed the deletion, {@code false} if the deletion needs to be performed by the platform.
   */
  boolean delete(@NotNull VirtualFile file) throws IOException;

  /**
   * Intercepts the movement of a file.
   * @param file  the file being moved.
   * @param toDir the destination directory.
   * @return {@code true} if the handler has performed the move, {@code false} if the move needs to be performed by the platform.
   */
  boolean move(@NotNull VirtualFile file, @NotNull VirtualFile toDir) throws IOException;

  /**
   * Intercepts the copying of a file.
   * @param file  the file being copied.
   * @param toDir the destination directory.
   * @param copyName the name for the copy
   * @return {@code true} if the handler has performed the copy, {@code false} if the copy needs to be performed by the platform.
   */
  default boolean copyFile(@NotNull VirtualFile file, @NotNull VirtualFile toDir, @NotNull String copyName) throws IOException {
    return copy(file, toDir, copyName) != null;
  }

  /** @deprecated obsolete; implement {@link #copyFile(VirtualFile, VirtualFile, String)} instead */
  @Deprecated(forRemoval = true)
  @SuppressWarnings({"IO_FILE_USAGE", "UnnecessaryFullyQualifiedName", "unused"})
  default @Nullable java.io.File copy(@NotNull VirtualFile file, @NotNull VirtualFile toDir, @NotNull String copyName) throws IOException {
    throw new UnsupportedOperationException();
  }

  /**
   * Intercepts the renaming of a file.
   * @param file  the file being renamed.
   * @param newName the new name.
   * @return {@code true} if the handler has performed the rename, {@code false} if the rename needs to be performed by the platform.
   */
  boolean rename(@NotNull VirtualFile file, @NotNull String newName) throws IOException;

  /**
   * Intercepts the creation of a file.
   * @param dir  the directory in which the file is being created.
   * @param name the name of the new file.
   * @return {@code true} if the handler has performed the file creation, {@code false} if the creation needs to be performed by the platform.
   */
  boolean createFile(@NotNull VirtualFile dir, @NotNull String name) throws IOException;

  /**
   * Intercepts the creation of a directory.
   * @param dir  the directory in which the directory is being created.
   * @param name the name of the new directory.
   * @return {@code true} if the handler has performed the directory creation, {@code false} if the creation needs to be performed by the platform.
   */
  boolean createDirectory(@NotNull VirtualFile dir, @NotNull String name) throws IOException;

  /** @deprecated the parameter is pointless; override {@link #completed()} instead if needed */
  @Deprecated(forRemoval = true)
  @SuppressWarnings({"DeprecatedIsStillUsed", "unused"})
  default void afterDone(@NotNull ThrowableConsumer<? super LocalFileOperationsHandler, ? extends IOException> invoker) { }

  /**
   * Called after the operation is completed.
   */
  default void completed() {
    afterDone(handler -> { });
  }
}
