// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;

/**
 * Implement if your {@link com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem} could benefit from batch queries -- i.e.
 * if directory's children with their attributes could be collected significantly faster in 1 request, than in
 * N individual requests to {@link com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem#getAttributes(VirtualFile)}
 */
@ApiStatus.Internal
public interface BatchingFileSystem {
  /**
   * Returns a list of files in a directory along with their attributes.
   * When the {@code childrenNames} set is {@code null}, all files should be returned.
   * TODO RC: specify, should returned map be case-(in)sensitive, according to dir.isCaseSensitive(), or it should be a plain
   *          case-sensitive map, and it is up to client code to transform it, if needed?
   */
  @NotNull Map<@NotNull String, @NotNull FileAttributes> listWithAttributes(@NotNull VirtualFile dir, @Nullable Set<String> childrenNames);

  default @NotNull Map<@NotNull String, @NotNull FileAttributes> listWithAttributes(@NotNull VirtualFile dir) {
    return listWithAttributes(dir, null);
  }
}
