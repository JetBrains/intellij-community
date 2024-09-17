// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.fs

import com.intellij.platform.eel.path.EelPath
import java.time.ZonedDateTime

sealed interface EelFileInfo {
  val type: Type
  val permissions: Permissions

  /** If not supported, returns null. */
  val lastModifiedTime: ZonedDateTime?

  /** If not supported, returns null. */
  val lastAccessTime: ZonedDateTime?

  /** If not supported, returns null. */
  val creationTime: ZonedDateTime?

  sealed interface Type {
    interface Directory : Type, EelPosixFileInfo.Type {
      val sensitivity: CaseSensitivity
    }

    interface Regular : Type, EelPosixFileInfo.Type {
      val size: Long
    }

    interface Other : Type, EelPosixFileInfo.Type
  }

  sealed interface Permissions

  enum class CaseSensitivity {
    SENSITIVE,
    INSENSITIVE,
    UNKNOWN,
  }
}

interface EelPosixFileInfo : EelFileInfo {
  override val type: Type
  override val permissions: Permissions

  /** The device number of the inode. */
  val inodeDev: Long

  /** The inode number. */
  val inodeIno: Long

  sealed interface Type : EelFileInfo.Type {
    sealed interface Symlink : Type {
      interface Unresolved : Symlink
      interface Resolved : Symlink {
        val result: EelPath.Absolute
      }
    }
  }

  interface Permissions : EelFileInfo.Permissions {
    /** TODO */
    val owner: Int
    val group: Int

    val ownerCanRead: Boolean
    val ownerCanWrite: Boolean
    val ownerCanExecute: Boolean

    val groupCanRead: Boolean
    val groupCanWrite: Boolean
    val groupCanExecute: Boolean

    val otherCanRead: Boolean
    val otherCanWrite: Boolean
    val otherCanExecute: Boolean

    val setUid: Boolean
    val setGid: Boolean
    val stickyBit: Boolean
  }
}

interface EelWindowsFileInfo : EelFileInfo {
  override val permissions: Permissions

  interface Permissions : EelFileInfo.Permissions {
    val isReadOnly: Boolean
    val isHidden: Boolean
    val isArchive: Boolean
    val isSystem: Boolean
  }
}