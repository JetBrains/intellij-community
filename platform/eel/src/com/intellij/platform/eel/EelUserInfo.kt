// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel

import com.intellij.platform.eel.path.EelPath
import org.jetbrains.annotations.ApiStatus

/**
 * Information about the user that operations in an environment run as. Obtained from [EelApi.userInfo].
 *
 * [EelUserPosixInfo] and [EelUserWindowsInfo] are the OS-family-specific refinements.
 */
@ApiStatus.Experimental
sealed interface EelUserInfo {
  /** The user's home directory in the environment. */
  val home: EelPath
}

/**
 * POSIX [EelUserInfo]: the user's numeric IDs.
 */
@ApiStatus.Experimental
interface EelUserPosixInfo : EelUserInfo {
  /** The user's user ID (`uid`). */
  val uid: Int

  /** The user's primary group ID (`gid`). */
  val gid: Int
}

/**
 * Windows [EelUserInfo]. Currently exposes only the common [home]; Windows-specific details (e.g. the user's SID) are not modeled yet.
 */
@ApiStatus.Experimental
interface EelUserWindowsInfo : EelUserInfo {
  // TODO https://learn.microsoft.com/en-us/windows-server/identity/ad-ds/manage/understand-security-identifiers
}