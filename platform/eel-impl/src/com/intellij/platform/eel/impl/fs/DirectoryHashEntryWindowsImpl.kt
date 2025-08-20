// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.fs

import com.intellij.platform.eel.fs.DirectoryHashEntry
import com.intellij.platform.eel.fs.DirectoryHashEntryWindows
import com.intellij.platform.eel.path.EelPath
import java.time.ZonedDateTime

data class DirectoryHashEntryWindowsImpl(
  override val path: EelPath,
  override val type: DirectoryHashEntry.Type,
  override val attributes: DirectoryHashEntryWindows.Attributes,
  override val permissions: DirectoryHashEntryWindows.Permissions,
  override val creationTime: ZonedDateTime?,
  override val lastModifiedTime: ZonedDateTime?,
  override val lastAccessTime: ZonedDateTime?,
) : DirectoryHashEntryWindows {
  object Directory : DirectoryHashEntry.Type.Directory
  data class Regular(override val hash: Long) : DirectoryHashEntry.Type.Regular
  data class SymlinkAbsolute(override val symlinkAbsolutePath: EelPath) : DirectoryHashEntry.Type.Symlink.Absolute
  data class SymlinkRelative(override val symlinkRelativePath: String) : DirectoryHashEntry.Type.Symlink.Relative
  object Other : DirectoryHashEntry.Type.Other

  object Permissions : DirectoryHashEntryWindows.Permissions

  data class Attributes(
    override val isReadOnly: Boolean,
    override val isHidden: Boolean,
    override val isArchive: Boolean,
    override val isSystem: Boolean,
  ) : DirectoryHashEntryWindows.Attributes
}