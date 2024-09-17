// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel

/**
 * [architecture] is the remote architecture of the built binary. Intended to be used for debugging purposes.
 * [remotePid] is a process ID of IJent running on the remote machine.
 * [version] is the version of the IJent binary. Intended to be used for debugging purposes.
 */
sealed interface EelInfo {
  val architecture: String
  val remotePid: EelApi.Pid
  val version: String
  val user: User

  sealed interface User
}

interface EelPosixInfo : EelInfo {
  override val user: User

  interface User : EelInfo.User {
    val uid: Int
    val gid: Int
  }
}

interface EelWindowsInfo : EelInfo {
  override val user: User

  interface User : EelInfo.User {
    // TODO https://learn.microsoft.com/en-us/windows-server/identity/ad-ds/manage/understand-security-identifiers
  }
}