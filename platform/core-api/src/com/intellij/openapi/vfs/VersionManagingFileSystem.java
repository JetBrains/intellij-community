// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs;

import org.jetbrains.annotations.NotNull;

/**
 * Allows to disable local history creation for a specific file in a virtual file system implementation.
 */
public interface VersionManagingFileSystem {
  /**
   * Checks if a local history should be created for the file in question.
   * @param file The virtual file to check.
   * @return {@code true} if the file can be versioned. It's actual versioning in this case depends on other conditions, for example, if
   * the file is ignored. {@code false} if file's change history should not be tracked.
   */
  boolean isVersionable(@NotNull VirtualFile file);

}
