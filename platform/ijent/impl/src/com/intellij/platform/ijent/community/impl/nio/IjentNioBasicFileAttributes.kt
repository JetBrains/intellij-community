// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.community.impl.nio

import com.intellij.openapi.util.io.CaseSensitivityAttribute
import com.intellij.openapi.util.io.FileAttributes
import com.intellij.platform.eel.fs.EelFileInfo
import com.intellij.platform.eel.fs.EelFileInfo.Type.*
import com.intellij.platform.eel.fs.EelPosixFileInfo
import com.intellij.platform.eel.fs.EelPosixFileInfo.Type.Symlink
import com.intellij.platform.eel.fs.EelWindowsFileInfo
import java.nio.file.attribute.*
import java.nio.file.attribute.PosixFilePermission.*
import java.time.Instant
import java.util.*

internal class IjentNioBasicFileAttributes(private val fileInfo: EelFileInfo) : BasicFileAttributes {
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
      is EelPosixFileInfo -> EelUnixFileKey(dev = fileInfo.inodeDev, ino = fileInfo.inodeIno)
      is EelWindowsFileInfo -> TODO()
    }
}

/** Similar to `sun.nio.fs.UnixFileKey` */
internal data class EelUnixFileKey(val dev: Long, val ino: Long)

class IjentNioPosixFileAttributes(
  internal val fileInfo: EelPosixFileInfo,
) : CaseSensitivityAttribute, PosixFileAttributes, BasicFileAttributes by IjentNioBasicFileAttributes(fileInfo) {
  override fun owner(): UserPrincipal =
    EelPosixUserPrincipal(fileInfo.permissions.owner)

  override fun group(): GroupPrincipal =
    EelPosixGroupPrincipal(fileInfo.permissions.group)

  override fun permissions(): Set<PosixFilePermission> {
    val permissions = entries.filter { pfp ->
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
    }
    return if (permissions.isEmpty()) EnumSet.noneOf(PosixFilePermission::class.java) else EnumSet.copyOf(permissions)
  }

  override fun getCaseSensitivity(): FileAttributes.CaseSensitivity = when (val type = fileInfo.type) {
    is Directory -> when (type.sensitivity) {
      EelFileInfo.CaseSensitivity.SENSITIVE -> FileAttributes.CaseSensitivity.SENSITIVE
      EelFileInfo.CaseSensitivity.INSENSITIVE -> FileAttributes.CaseSensitivity.INSENSITIVE
      EelFileInfo.CaseSensitivity.UNKNOWN -> FileAttributes.CaseSensitivity.UNKNOWN
    }
    else -> throw IllegalStateException("Cannot ask for case sensitivity of $type")
  }
}

class EelPosixUserPrincipal(val uid: Int) : UserPrincipal {
  override fun getName(): String {
    // TODO Here should be returned a user name
    return uid.toString()
  }

  override fun toString(): String = "EelPosixUserPrincipal(uid=$uid)"

  override fun equals(other: Any?): Boolean = other is EelPosixUserPrincipal && uid == other.uid

  override fun hashCode(): Int = uid.hashCode()
}

class EelPosixGroupPrincipal(val gid: Int) : GroupPrincipal {
  override fun getName(): String {
    // TODO Here should be returned a user name
    return gid.toString()
  }

  override fun toString(): String = "EelPosixGroupPrincipal(gid=$gid)"

  override fun equals(other: Any?): Boolean = other is EelPosixGroupPrincipal && gid == other.gid

  override fun hashCode(): Int = gid.hashCode()
}