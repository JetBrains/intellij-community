// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs;

import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

/**
 * VirtualFileSystem interface to control file appearance in a local history.
 */
public interface VersionManagingFileSystem {
  enum VersioningType {
    /**
     * File is excluded from local history. Used to explicitly suppress file history collection.
     */
    DISABLED,

    /**
     * File's history is managed by the IDE's local history manager if it's a local source file.
     */
    LOCAL,

    /**
     * File's history is managed by the IDE's local history manager even if it's a non-local file.
     *
     */
    ENFORCED_NON_LOCAL
  }

  /**
   * Determines a local history type of the given virtual file.
   *
   * @param file The file to check.
   * @return File versioning {@link VersioningType}
   */
  VersioningType getVersioningType(@NotNull VirtualFile file);

  /**
   * A helper method to check if file's history is enforced for the file from a non-local file system.
   *
   * @param file The file to check.
   * @return True if file's history is managed by the local history manager.
   */
  static boolean isEnforcedNonLocal(@NotNull VirtualFile file) {
    return ObjectUtils.doIfCast(file.getFileSystem(), VersionManagingFileSystem.class,
                                fs -> fs.getVersioningType(file)) == VersioningType.ENFORCED_NON_LOCAL;
  }

  /**
   * A helper method to check if file's history is disabled by the managing file system.
   *
   * @param file The file to check.
   * @return True if file's history is disabled.
   */
  static boolean isDisabled(@NotNull VirtualFile file) {
    return ObjectUtils.doIfCast(file.getFileSystem(), VersionManagingFileSystem.class,
                                fs -> fs.getVersioningType(file)) == VersioningType.DISABLED;
  }
}
