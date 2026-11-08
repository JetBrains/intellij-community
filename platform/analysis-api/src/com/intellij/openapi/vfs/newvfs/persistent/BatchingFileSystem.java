// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.FileStateUnknownException;
import com.intellij.openapi.vfs.newvfs.FileSystemUnavailableException;
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;

/// Defines more 'low-level' FS capabilities, in additional to general [NewVirtualFileSystem]:
/// - **Batch queries**: implement [#listWithAttributes] if directory's children with their attributes could be collected
///   significantly faster in 1 request than in N individual requests to [NewVirtualFileSystem#getAttributes(VirtualFile)];
/// - **Error processing/unavailability**: methods throw [FileStateUnknownException] when the FS _cannot tell_ the state
///   of a file/directory -- as opposed to the file/directory just not exists, in which case an empty/null result is returned;
///
/// More about error processing/unavailability: [NewVirtualFileSystem] methods provide no way to signal 'information is not
/// available now'. If file or directory information is not available due to some error (lack of permissions, network disconnect,
/// etc), [NewVirtualFileSystem#getAttributes(VirtualFile)] and [NewVirtualFileSystem#list(VirtualFile)] methods just return
/// "empty" result -- as-if the file is genuine not exist, or directory is genuine empty. This loses important distinction,
/// because sometimes information about file/dir _could_ still be obtained in a different way.
///
/// E.g. the directory _listing_ could be prohibited for a user, but access to specific files in the directory could still be
/// allowed -- so the failure of `list(dir)` doesn't always mean directory is empty, one could still try `getAttributes(file)`
/// on specific file.
///
/// Another example is network disconnect for remote FSes: disconnect is temporary, and the fact that right now file/dir info
/// is not available doesn't mean file/dir is really not exist/empty -- later attempts could succeed in getting the info.
@ApiStatus.Internal
public interface BatchingFileSystem {
  /// @return directory's children, in the form of a map of names to file attributes: a name that is absent from the map is absent from
  /// the directory.
  /// When the `childrenNames` is not-null, only those children's data should be returned (if exist), otherwise, all directory's
  /// children should be returned.
  /// The returned map should be a regular, case-sensitive map – it is up to calling code to covert it to case-insensitive, if needed.
  /// @throws FileStateUnknownException if the FS cannot read the directory now
  /// @throws FileSystemUnavailableException if the whole FS cannot be accessed now
  //Why contract defines returned map to be case-sensitive? Because dir.isCaseSensitive() is tricky, and sometimes it's value could
  // be unreliable/outdated -- in which case this method would provide incorrect info, be it defined to return map with case-sensitivity
  // =dir.isCaseSensitive().
  // But the responsibility of (Batching)FileSystem is _only_ to be golden-source of info about actual file system state -- it is
  // the responsibility of the caller (=VFS) to deal with (possible) inconsistencies between VFS state and actual FS state.
  //MAYBE RC: return List<Pair<String,FileAttributes>>? this way question of case-sensitivity is not even on the table
  @NotNull Map<@NotNull String, @NotNull FileAttributes> listWithAttributes(
    @NotNull VirtualFile dir,
    @Nullable Set<String> childrenNames
  ) throws FileStateUnknownException;

  default @NotNull Map<@NotNull String, @NotNull FileAttributes> listWithAttributes(@NotNull VirtualFile dir) throws FileStateUnknownException {
    return listWithAttributes(dir, null);
  }

  /// [NewVirtualFileSystem#getAttributes(VirtualFile)] that does not answer "absent" for an error
  /// @return the attributes of `file`, or `null` if the file does not exist
  /// @throws FileStateUnknownException if the FS cannot tell whether the file exists
  /// @throws FileSystemUnavailableException if the whole FS cannot be accessed now
  @Nullable FileAttributes getAttributesOrThrow(@NotNull VirtualFile file) throws FileStateUnknownException;
}
