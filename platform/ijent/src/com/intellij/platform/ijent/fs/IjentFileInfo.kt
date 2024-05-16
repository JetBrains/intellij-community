// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.fs

import java.time.ZonedDateTime

sealed interface IjentFileInfo {
  val path: IjentPath.Absolute
  val type: Type
  val permissions: Permissions

  /** If not supported, returns null. */
  val lastModifiedTime: ZonedDateTime?

  /** If not supported, returns null. */
  val lastAccessTime: ZonedDateTime?

  /** If not supported, returns null. */
  val creationTime: ZonedDateTime?

  sealed interface Type {
    interface Directory : Type, IjentPosixFileInfo.Type

    interface Regular : Type, IjentPosixFileInfo.Type {
      val size: Long
    }

    interface Other : Type, IjentPosixFileInfo.Type
  }

  sealed interface Permissions
}

interface IjentPosixFileInfo : IjentFileInfo {
  override val type: Type
  override val permissions: Permissions

  sealed interface Type : IjentFileInfo.Type {
    sealed interface Symlink : Type {
      interface Unresolved : Symlink
      interface Resolved : Symlink {
        val result: IjentPath.Absolute
      }
    }
  }

  interface Permissions : IjentFileInfo.Permissions {
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

interface IjentWindowsFileInfo : IjentFileInfo {
  override val permissions: Permissions

  interface Permissions : IjentFileInfo.Permissions {
    val isReadOnly: Boolean
    val isHidden: Boolean
    val isArchive: Boolean
    val isSystem: Boolean
  }
}