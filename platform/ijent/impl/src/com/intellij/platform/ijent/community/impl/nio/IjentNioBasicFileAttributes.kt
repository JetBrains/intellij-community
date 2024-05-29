// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.community.impl.nio

import com.intellij.platform.ijent.fs.IjentFileInfo
import com.intellij.platform.ijent.fs.IjentFileInfo.Type.*
import com.intellij.platform.ijent.fs.IjentPosixFileInfo
import com.intellij.platform.ijent.fs.IjentPosixFileInfo.Type.Symlink
import com.intellij.platform.ijent.fs.IjentWindowsFileInfo
import java.nio.file.attribute.*
import java.nio.file.attribute.PosixFilePermission.*
import java.time.Instant
import java.util.*

internal class IjentNioBasicFileAttributes(private val fileInfo: IjentFileInfo) : BasicFileAttributes {
  override fun lastModifiedTime(): FileTime =
    FileTime.from(fileInfo.lastModifiedTime?.toInstant() ?: Instant.MIN)

  override fun lastAccessTime(): FileTime =
    FileTime.from(fileInfo.lastAccessTime?.toInstant() ?: Instant.MIN)

  override fun creationTime(): FileTime =
    FileTime.from(fileInfo.creationTime?.toInstant() ?: Instant.MIN)

  override fun isRegularFile(): Boolean =
    when (fileInfo.type) {
      is Regular -> true
      is Directory, is Other, is Symlink -> false
    }

  override fun isDirectory(): Boolean =
    when (fileInfo.type) {
      is Directory -> true
      is Other, is Regular, is Symlink -> false
    }

  override fun isSymbolicLink(): Boolean =
    when (fileInfo.type) {
      is Symlink -> true
      is Directory, is Other, is Regular -> false
    }

  override fun isOther(): Boolean =
    when (fileInfo.type) {
      is Other -> true
      is Directory, is Regular, is Symlink -> false
    }

  override fun size(): Long =
    when (val t = fileInfo.type) {
      is Regular -> t.size

      // 0 is what WindowsFileSystemProvider returns for directories on WSL.
      // It wasn't checked what other providers return in other cases.
      is Directory, is Other, is Symlink.Resolved, is Symlink.Unresolved -> 0
    }

  override fun fileKey(): Any =
    when (fileInfo) {
      is IjentPosixFileInfo -> IjentUnixFileKey(dev = fileInfo.inodeDev, ino = fileInfo.inodeIno)
      is IjentWindowsFileInfo -> TODO()
    }
}

/** Similar to `sun.nio.fs.UnixFileKey` */
internal data class IjentUnixFileKey(val dev: Long, val ino: Long)

internal class IjentNioPosixFileAttributes(
  private val fileInfo: IjentPosixFileInfo,
) : PosixFileAttributes, BasicFileAttributes by IjentNioBasicFileAttributes(fileInfo) {
  override fun owner(): UserPrincipal =
    IjentPosixUserPrincipal(fileInfo.permissions.owner)

  override fun group(): GroupPrincipal =
    IjentPosixGroupPrincipal(fileInfo.permissions.group)

  override fun permissions(): Set<PosixFilePermission> =
    EnumSet.copyOf(PosixFilePermission.values().filter { pfp ->
      when (pfp) {
        OWNER_READ -> fileInfo.permissions.ownerCanRead
        OWNER_WRITE -> fileInfo.permissions.ownerCanWrite
        OWNER_EXECUTE -> fileInfo.permissions.ownerCanExecute
        GROUP_READ -> fileInfo.permissions.groupCanRead
        GROUP_WRITE -> fileInfo.permissions.groupCanWrite
        GROUP_EXECUTE -> fileInfo.permissions.groupCanExecute
        OTHERS_READ -> fileInfo.permissions.otherCanRead
        OTHERS_WRITE -> fileInfo.permissions.otherCanWrite
        OTHERS_EXECUTE -> fileInfo.permissions.otherCanExecute
      }
    })
}

class IjentPosixUserPrincipal(val uid: Int) : UserPrincipal {
  override fun getName(): String {
    // TODO Here should be returned a user name
    return uid.toString()
  }
}

class IjentPosixGroupPrincipal(val gid: Int) : GroupPrincipal {
  override fun getName(): String {
    // TODO Here should be returned a user name
    return gid.toString()
  }
}