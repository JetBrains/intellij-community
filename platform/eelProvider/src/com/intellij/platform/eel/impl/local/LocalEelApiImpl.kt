// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.local

import com.intellij.openapi.util.SystemInfo
import com.intellij.platform.eel.*
import com.intellij.platform.eel.fs.EelFileSystemApi
import com.intellij.platform.eel.fs.EelFileSystemPosixApi
import com.intellij.platform.eel.fs.EelFileSystemWindowsApi
import com.intellij.platform.eel.fs.getPath
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.EelUserPosixInfoImpl
import com.intellij.platform.eel.provider.EelUserWindowsInfoImpl
import kotlinx.coroutines.CoroutineScope
import java.nio.file.Path

internal class LocalEelPathMapper(private val eelApi: EelApi) : EelPathMapper {
  override fun getOriginalPath(path: Path): EelPath.Absolute {
    return eelApi.fs.getPath(path.toString())
  }

  override suspend fun maybeUploadPath(path: Path, scope: CoroutineScope, options: EelFileSystemApi.CreateTemporaryDirectoryOptions): EelPath.Absolute {
    return getOriginalPath(path)
  }

  override fun toNioPath(path: EelPath.Absolute): Path {
    return Path.of(path.toString())
  }
}

internal class LocalWindowsEelApiImpl : LocalEelApi, EelWindowsApi {
  init {
    check(SystemInfo.isWindows)
  }

  override val tunnels: EelTunnelsWindowsApi get() = TODO("Not yet implemented")
  override val platform: EelPlatform.Windows get() = if (SystemInfo.isAarch64) TODO("Not yet implemented") else EelPlatform.X64Windows
  override val exec: EelExecApi = EelLocalExecApi()
  override val userInfo: EelUserWindowsInfo = EelUserWindowsInfoImpl
  override val mapper: EelPathMapper = LocalEelPathMapper(this)

  override val fs: EelFileSystemWindowsApi
    get() = TODO("Not yet implemented")
}

internal class LocalPosixEelApiImpl : LocalEelApi, EelPosixApi {
  init {
    check(SystemInfo.isUnix)
  }

  override val tunnels: EelTunnelsPosixApi get() = TODO("Not yet implemented")
  override val platform: EelPlatform.Posix = if (SystemInfo.isAarch64) EelPlatform.Aarch64Linux else EelPlatform.X8664Linux
  override val exec: EelExecApi = EelLocalExecApi()
  override val mapper: EelPathMapper = LocalEelPathMapper(this)

  override val userInfo: EelUserPosixInfo = EelUserPosixInfoImpl(
    uid = System.getProperty("user.id").toInt(),
    gid = System.getProperty("group.id").toInt(),
  )
  override val fs: EelFileSystemPosixApi
    get() = TODO("Not yet implemented")
}