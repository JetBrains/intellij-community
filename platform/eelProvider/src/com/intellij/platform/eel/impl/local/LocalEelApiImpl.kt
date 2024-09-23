// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.local

import com.intellij.openapi.util.SystemInfo
import com.intellij.platform.eel.*

internal class LocalWindowsEelApiImpl : LocalEelApi, EelWindowsApi {
  init {
    check(SystemInfo.isWindows)
  }

  override val tunnels: EelTunnelsWindowsApi get() = TODO("Not yet implemented")
  override val platform: EelPlatform.Windows get() = if (SystemInfo.isAarch64) TODO("Not yet implemented") else EelPlatform.X64Windows
  override val exec: EelExecApi = EelLocalExecApi()

}

internal class LocalPosixEelApiImpl : LocalEelApi, EelPosixApi {
  init {
    check(SystemInfo.isUnix)
  }

  override val tunnels: EelTunnelsPosixApi get() = TODO("Not yet implemented")
  override val platform: EelPlatform.Posix = if (SystemInfo.isAarch64) EelPlatform.Aarch64Linux else EelPlatform.X8664Linux
  override val exec: EelExecApi = EelLocalExecApi()
}