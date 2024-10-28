// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel

import com.intellij.platform.eel.path.EelPath

sealed interface EelUserInfo {
  val home: EelPath.Absolute
}

interface EelUserPosixInfo : EelUserInfo {
  val uid: Int
  val gid: Int
}

interface EelUserWindowsInfo : EelUserInfo {
  // TODO https://learn.microsoft.com/en-us/windows-server/identity/ad-ds/manage/understand-security-identifiers
}