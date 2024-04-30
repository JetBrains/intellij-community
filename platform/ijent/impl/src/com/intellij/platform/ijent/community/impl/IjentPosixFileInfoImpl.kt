// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.community.impl

import com.intellij.platform.ijent.fs.IjentFileInfo
import com.intellij.platform.ijent.fs.IjentPath
import com.intellij.platform.ijent.fs.IjentPosixFileInfo
import org.jetbrains.annotations.ApiStatus.Internal
import java.time.ZonedDateTime

@Internal
data class IjentPosixFileInfoImpl(
  override val path: IjentPath.Absolute,
  override val type: IjentPosixFileInfo.Type,
  override val permissions: IjentPosixFileInfo.Permissions,
  override val creationTime: ZonedDateTime?,
  override val lastModifiedTime: ZonedDateTime?,
  override val lastAccessTime: ZonedDateTime?,
) : IjentPosixFileInfo {
  data object Directory : IjentFileInfo.Type.Directory
  data class Regular(override val size: Long) : IjentFileInfo.Type.Regular
  data class SymlinkResolved(override val result: IjentPath.Absolute) : IjentPosixFileInfo.Type.Symlink.Resolved
  data object SymlinkUnresolved : IjentPosixFileInfo.Type.Symlink.Unresolved
  data object Other : IjentFileInfo.Type.Other

  data class Permissions(
    override val owner: Int,
    override val group: Int,
    val mask: Int,
  ) : IjentPosixFileInfo.Permissions {
    override val otherCanExecute: Boolean get() = (mask and 0x1) != 0 // (00001)  execute/search by others
    override val otherCanWrite: Boolean get() = (mask and 0x2) != 0 // (00002)  write by others
    override val otherCanRead: Boolean get() = (mask and 0x4) != 0 // (00004)  read by others
    override val groupCanExecute: Boolean get() = (mask and 0x8) != 0 // (00010)  execute/search by group
    override val groupCanWrite: Boolean get() = (mask and 0x10) != 0 // (00020)  write by group
    override val groupCanRead: Boolean get() = (mask and 0x20) != 0 // (00040)  read by group
    override val ownerCanExecute: Boolean get() = (mask and 0x40) != 0 // (00100)  execute/search by owner
    override val ownerCanWrite: Boolean get() = (mask and 0x80) != 0 // (00200)  write by owner
    override val ownerCanRead: Boolean get() = (mask and 0x100) != 0 // (00400)  read by owner
    override val stickyBit: Boolean get() = (mask and 0x200) != 0 // (01000)  sticky bit
    override val setGid: Boolean get() = (mask and 0x400) != 0 // (02000)  set-group-ID
    override val setUid: Boolean get() = (mask and 0x800) != 0 // (04000)  set-user-ID
  }
}