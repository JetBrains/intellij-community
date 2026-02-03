// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.fs

import com.intellij.platform.eel.fs.WalkDirectoryEntry
import com.intellij.platform.eel.fs.WalkDirectoryEntryPosix
import com.intellij.platform.eel.path.EelPath
import org.jetbrains.annotations.ApiStatus
import java.nio.file.attribute.PosixFilePermission
import java.time.ZonedDateTime

@ApiStatus.Internal
data class WalkDirectoryEntryPosixImpl(
  override val path: EelPath,
  override val type: WalkDirectoryEntry.Type,
  override val permissions: WalkDirectoryEntryPosix.Permissions?,
  override val attributes: WalkDirectoryEntryPosix.Attributes?,
  override val creationTime: ZonedDateTime?,
  override val lastModifiedTime: ZonedDateTime?,
  override val lastAccessTime: ZonedDateTime?,
) : WalkDirectoryEntryPosix {
  object Directory : WalkDirectoryEntry.Type.Directory
  data class Regular(override val hash: Long?) : WalkDirectoryEntry.Type.Regular
  data class SymlinkAbsolute(override val symlinkAbsolutePath: EelPath) : WalkDirectoryEntry.Type.Symlink.Absolute
  data class SymlinkRelative(override val symlinkRelativePath: String) : WalkDirectoryEntry.Type.Symlink.Relative
  object Other : WalkDirectoryEntry.Type.Other

  object Attributes : WalkDirectoryEntryPosix.Attributes

  data class Permissions(
    override val owner: Int,
    override val group: Int,
    override val mask: Int,
    override val permissionsSet: Set<PosixFilePermission>,
  ) : WalkDirectoryEntryPosix.Permissions {
    override val otherCanExecute: Boolean get() = (mask and 0x1) != 0 // (00001) execute/search by others
    override val otherCanWrite: Boolean get() = (mask and 0x2) != 0 // (00002) write by others
    override val otherCanRead: Boolean get() = (mask and 0x4) != 0 // (00004) read by others
    override val groupCanExecute: Boolean get() = (mask and 0x8) != 0 // (00010) execute/search by group
    override val groupCanWrite: Boolean get() = (mask and 0x10) != 0 // (00020) write by group
    override val groupCanRead: Boolean get() = (mask and 0x20) != 0 // (00040) read by group
    override val ownerCanExecute: Boolean get() = (mask and 0x40) != 0 // (00100) execute/search by owner
    override val ownerCanWrite: Boolean get() = (mask and 0x80) != 0 // (00200) write by owner
    override val ownerCanRead: Boolean get() = (mask and 0x100) != 0 // (00400) read by owner
    override val stickyBit: Boolean get() = (mask and 0x200) != 0 // (01000) sticky bit
    override val setGid: Boolean get() = (mask and 0x400) != 0 // (02000) set-group-ID
    override val setUid: Boolean get() = (mask and 0x800) != 0 // (04000) set-user-ID
  }
}