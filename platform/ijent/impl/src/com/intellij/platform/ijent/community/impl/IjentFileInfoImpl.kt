// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.community.impl

import com.intellij.platform.eel.EelApi
import com.intellij.platform.ijent.IjentPosixInfo
import com.intellij.platform.ijent.IjentWindowsInfo

data class IjentPosixInfoImpl(
  override val architecture: String,
  override val remotePid: EelApi.Pid,
  override val version: String,
  override val user: IjentPosixInfo.User,
) : IjentPosixInfo

data class IjentWindowsInfoImpl(
  override val architecture: String,
  override val remotePid: EelApi.Pid,
  override val version: String,
  override val user: IjentWindowsInfo.User,
) : IjentWindowsInfo

data class IjentPosixInfoUserImpl(
  override val uid: Int,
  override val gid: Int
) : IjentPosixInfo.User

data object IjentWindowsInfoUserImpl : IjentWindowsInfo.User