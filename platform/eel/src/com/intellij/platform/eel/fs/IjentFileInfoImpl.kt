// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.fs

import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelPosixInfo
import com.intellij.platform.eel.EelWindowsInfo

data class EelPosixInfoImpl(
  override val architecture: String,
  override val remotePid: EelApi.Pid,
  override val version: String,
  override val user: EelPosixInfo.User,
) : EelPosixInfo

data class EelWindowsInfoImpl(
  override val architecture: String,
  override val remotePid: EelApi.Pid,
  override val version: String,
  override val user: EelWindowsInfo.User,
) : EelWindowsInfo

data class EelPosixInfoUserImpl(
  override val uid: Int,
  override val gid: Int
) : EelPosixInfo.User

data object EelWindowsInfoUserImpl : EelWindowsInfo.User