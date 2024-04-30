// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.community.impl

import com.intellij.platform.ijent.IjentApi
import com.intellij.platform.ijent.IjentPosixInfo
import com.intellij.platform.ijent.IjentWindowsInfo
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
data class IjentPosixInfoImpl(
  override val architecture: String,
  override val remotePid: IjentApi.Pid,
  override val version: String,
  override val user: IjentPosixInfo.User,
) : IjentPosixInfo

@Internal
data class IjentWindowsInfoImpl(
  override val architecture: String,
  override val remotePid: IjentApi.Pid,
  override val version: String,
  override val user: IjentWindowsInfo.User,
) : IjentWindowsInfo

@Internal
data class IjentPosixInfoUserImpl(
  override val uid: Int,
  override val gid: Int
) : IjentPosixInfo.User

@Internal
data object IjentWindowsInfoUserImpl : IjentWindowsInfo.User