// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.fs

import com.intellij.platform.eel.path.EelPath
import org.jetbrains.annotations.ApiStatus
import java.nio.file.attribute.PosixFilePermission
import java.time.ZonedDateTime

@ApiStatus.Internal
sealed interface DirectoryHashEntry {
  val path: EelPath
  val type: Type

  sealed interface Type {
    interface Directory : Type
    interface Regular : Type {
      val hash: Long
    }

    interface Other : Type
    sealed interface Symlink : Type {
      // Symlink contains an absolute path
      interface Absolute : Symlink {
        val symlinkAbsolutePath: EelPath
      }

      // Symlink contains a relative path
      interface Relative : Symlink {
        val symlinkRelativePath: String
      }
    }
  }

  /** Attributes refer to:
   *  DOS Attributes (isReadOnly, isArchive...)
   *  Linux FS Attribute Flags (append only, immutable...) NOTE: can be implemented if needed
   *  macOS FS Attributes (uappnd, uchg...) NOTE: can be implemented if needed
   **/
  sealed interface Attributes

  val attributes: Attributes

  sealed interface Permissions

  val permissions: Permissions

  /** If not supported, returns null.
   **/
  val lastModifiedTime: ZonedDateTime?

  /** If not supported, returns null. */
  val lastAccessTime: ZonedDateTime?

  /** If not supported, returns null. */
  val creationTime: ZonedDateTime?
}

@ApiStatus.Internal
interface DirectoryHashEntryPosix : DirectoryHashEntry {
  override val attributes: Attributes

  interface Attributes : DirectoryHashEntry.Attributes

  override val permissions: Permissions

  interface Permissions : DirectoryHashEntry.Permissions {
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

    val mask: Int
    val permissionsSet: Set<PosixFilePermission>
  }
}

@ApiStatus.Internal
interface DirectoryHashEntryWindows : DirectoryHashEntry {
  override val permissions: Permissions

  interface Permissions : DirectoryHashEntry.Permissions

  override val attributes: Attributes

  interface Attributes : DirectoryHashEntry.Attributes {
    val isReadOnly: Boolean
    val isHidden: Boolean
    val isArchive: Boolean
    val isSystem: Boolean
  }
}
