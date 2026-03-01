// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.fs

import com.intellij.platform.eel.fs.WalkDirectoryEntry
import com.intellij.platform.eel.fs.WalkDirectoryEntryWindows
import com.intellij.platform.eel.path.EelPath
import org.jetbrains.annotations.ApiStatus
import java.time.ZonedDateTime

@ApiStatus.Internal
data class WalkDirectoryEntryWindowsImpl(
  override val path: EelPath,
  override val type: WalkDirectoryEntry.Type,
  override val attributes: WalkDirectoryEntryWindows.Attributes?,
  override val permissions: WalkDirectoryEntryWindows.Permissions?,
  override val creationTime: ZonedDateTime?,
  override val lastModifiedTime: ZonedDateTime?,
  override val lastAccessTime: ZonedDateTime?,
) : WalkDirectoryEntryWindows {
  object Directory : WalkDirectoryEntry.Type.Directory
  data class Regular(override val hash: Long?) : WalkDirectoryEntry.Type.Regular
  data class SymlinkAbsolute(override val symlinkAbsolutePath: EelPath) : WalkDirectoryEntry.Type.Symlink.Absolute
  data class SymlinkRelative(override val symlinkRelativePath: String) : WalkDirectoryEntry.Type.Symlink.Relative
  object Other : WalkDirectoryEntry.Type.Other

  object Permissions : WalkDirectoryEntryWindows.Permissions

  data class Attributes(
    override val isReadOnly: Boolean,
    override val isHidden: Boolean,
    override val isArchive: Boolean,
    override val isSystem: Boolean,
  ) : WalkDirectoryEntryWindows.Attributes
}