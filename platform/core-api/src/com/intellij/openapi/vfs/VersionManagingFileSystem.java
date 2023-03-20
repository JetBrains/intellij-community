// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs;

import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

/**
 * VirtualFileSystem interface to control file appearance in a local history.
 */
public interface VersionManagingFileSystem {
  enum Type {
    /**
     * File is excluded from local history. Used to explicitly suppress file history collection.
     */
    DISABLED,

    /**
     * File's history by the IDE's own local history manager.
     */
    LOCAL,

    /**
     * File's history is managed by the virtual file system.
     *
     */
    FILE_SYSTEM
  }

  /**
   * Determines a versioning type of the given virtual file.
   *
   * @param file The file to check.
   * @return File versioning {@link Type}
   */
  Type getVersioningType(@NotNull VirtualFile file);

  /**
   * A helper method to check if file's history is managed by the virtual file system extending {@code VersionManagingFileSystem} interface.
   *
   * @param file The file to check.
   * @return True if file's history is managed by the file system.
   */
  static boolean isFsSupported(@NotNull VirtualFile file) {
    return ObjectUtils.doIfCast(file.getFileSystem(), VersionManagingFileSystem.class,
                                fs -> fs.getVersioningType(file)) == Type.FILE_SYSTEM;
  }

  /**
   * A helper method to check if file's history is disabled by the managing file system.
   *
   * @param file The file to check.
   * @return True if file's history is disabled.
   */
  static boolean isDisabled(@NotNull VirtualFile file) {
    return ObjectUtils.doIfCast(file.getFileSystem(), VersionManagingFileSystem.class,
                                fs -> fs.getVersioningType(file)) == Type.DISABLED;
  }
}
