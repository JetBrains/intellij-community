// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface LocalFileProvider {
  /** @deprecated use {@link com.intellij.openapi.vfs.newvfs.ArchiveFileSystem#getLocalByEntry(VirtualFile)} instead */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  VirtualFile getLocalVirtualFileFor(@Nullable VirtualFile entryVFile);

  /** @deprecated use {@code ArchiveFileSystem.findFileByPath(path)} instead */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  VirtualFile findLocalVirtualFileByPath(@NotNull String path);
}