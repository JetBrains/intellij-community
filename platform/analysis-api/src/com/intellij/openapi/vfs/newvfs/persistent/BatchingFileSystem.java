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
   * @return directory's children, in form of Map[childName-> FileAttributes]
   * When the {@code childrenNames} is not-null, only those children's data should be returned (if exists!),
   * when {@code childrenNames} is null -> all directory's children should be returned.
   * Returned map should be a 'normal' (i.e., case-sensitive) map -- it is up to calling code to covert it to case-insensitive, if
   * needed.
   */
  //Why contract defines returned map to be case-sensitive? Because dir.isCaseSensitive() is tricky, and sometimes it's value could
  // be unreliable/outdated -- in which case this method would provide incorrect info, be it defined to return map with case-sensitivity
  // =dir.isCaseSensitive().
  // But the responsibility of (Batching)FileSystem is _only_ to be golden-source of info about actual file system state -- it is
  // the responsibility of the caller (=VFS) to deal with (possible) inconsistencies between VFS state and actual FS state.
  //MAYBE RC: return List<Pair<String,FileAttributes>>? this way question of case-sensitivity is not even on the table
  @NotNull Map<@NotNull String, @NotNull FileAttributes> listWithAttributes(@NotNull VirtualFile dir,
                                                                            @Nullable Set<String> childrenNames);

  default @NotNull Map<@NotNull String, @NotNull FileAttributes> listWithAttributes(@NotNull VirtualFile dir) {
    return listWithAttributes(dir, null);
  }
}
