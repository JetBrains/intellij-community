// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.provider

import com.intellij.platform.eel.EelUserPosixInfo
import com.intellij.platform.eel.EelUserWindowsInfo

data class EelUserPosixInfoImpl(
  override val uid: Int,
  override val gid: Int,
) : EelUserPosixInfo

data object EelUserWindowsInfoImpl : EelUserWindowsInfo