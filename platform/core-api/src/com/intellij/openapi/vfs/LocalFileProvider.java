// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs;

import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** @deprecated use {@link com.intellij.openapi.vfs.newvfs.ArchiveFileSystem} instead */
@Deprecated
@ApiStatus.ScheduledForRemoval
public interface LocalFileProvider {
  /** @deprecated use {@link com.intellij.openapi.vfs.newvfs.ArchiveFileSystem#getLocalByEntry(VirtualFile)} instead */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  default VirtualFile getLocalVirtualFileFor(@Nullable VirtualFile entryVFile) {
    if (entryVFile != null && URLUtil.JAR_PROTOCOL.equals(entryVFile.getFileSystem().getProtocol())) {
      String entryPath = entryVFile.getPath();
      int p = entryPath.indexOf(URLUtil.JAR_SEPARATOR);
      if (p > 0) {
        return StandardFileSystems.local().findFileByPath(entryPath.substring(0, p));
      }
    }
    return null;
  }

  /** @deprecated use {@code ArchiveFileSystem.findFileByPath(path)} instead */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  default VirtualFile findLocalVirtualFileByPath(@SuppressWarnings("unused") @NotNull String path) {
    return null;
  }
}
