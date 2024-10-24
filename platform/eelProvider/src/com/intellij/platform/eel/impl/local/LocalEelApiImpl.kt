// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.local

import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.platform.eel.*
import com.intellij.platform.eel.fs.EelFileSystemApi
import com.intellij.platform.eel.fs.EelFileSystemApi.CreateTemporaryDirectoryError
import com.intellij.platform.eel.fs.EelFileSystemPosixApi
import com.intellij.platform.eel.fs.EelFileSystemWindowsApi
import com.intellij.platform.eel.fs.getPath
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.EelFsResultImpl.Error
import com.intellij.platform.eel.provider.EelFsResultImpl.Ok
import com.intellij.platform.eel.provider.EelFsResultImpl.Other
import com.intellij.platform.eel.provider.EelUserPosixInfoImpl
import com.intellij.platform.eel.provider.EelUserWindowsInfoImpl
import com.intellij.platform.eel.provider.fs.PosixNioBasedEelFileSystemApi
import com.intellij.platform.eel.provider.fs.WindowsNioBasedEelFileSystemApi
import com.intellij.util.text.nullize
import com.sun.security.auth.module.UnixSystem
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.VisibleForTesting
import java.nio.file.FileSystem
import java.nio.file.FileSystems
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

internal class LocalWindowsEelApiImpl(nioFs: FileSystem = FileSystems.getDefault()) : LocalEelApi, EelWindowsApi {
  init {
    check(SystemInfo.isWindows)
  }

  override val tunnels: EelTunnelsWindowsApi get() = TODO("Not yet implemented")
  override val platform: EelPlatform.Windows get() = if (SystemInfo.isAarch64) TODO("Not yet implemented") else EelPlatform.X64Windows
  override val exec: EelExecApi = EelLocalExecApi()
  override val userInfo: EelUserWindowsInfo = EelUserWindowsInfoImpl
  override val mapper: EelPathMapper = LocalEelPathMapper(this)
  override val archive: EelArchiveApi = LocalEelArchiveApiImpl

  override val fs: EelFileSystemWindowsApi = object : WindowsNioBasedEelFileSystemApi(nioFs, userInfo) {
    override suspend fun createTemporaryDirectory(
      options: EelFileSystemApi.CreateTemporaryDirectoryOptions,
    ): EelResult<EelPath.Absolute, CreateTemporaryDirectoryError> =
      doCreateTemporaryDirectory(mapper, options)
  }
}

@VisibleForTesting
class LocalPosixEelApiImpl(nioFs: FileSystem = FileSystems.getDefault()) : LocalEelApi, EelPosixApi {
  init {
    check(SystemInfo.isUnix)
  }

  override val tunnels: EelTunnelsPosixApi get() = TODO("Not yet implemented")
  override val platform: EelPlatform.Posix = if (SystemInfo.isAarch64) EelPlatform.Aarch64Linux else EelPlatform.X8664Linux
  override val exec: EelExecApi = EelLocalExecApi()
  override val mapper: EelPathMapper = LocalEelPathMapper(this)
  override val archive: EelArchiveApi = LocalEelArchiveApiImpl

  override val userInfo: EelUserPosixInfo = run {
    val unix = UnixSystem()
    EelUserPosixInfoImpl(uid = unix.uid.toInt(), gid = unix.gid.toInt())
  }

  override val fs: EelFileSystemPosixApi = object : PosixNioBasedEelFileSystemApi(nioFs, userInfo) {
    override val pathOs: EelPath.Absolute.OS = EelPath.Absolute.OS.UNIX

    override suspend fun createTemporaryDirectory(
      options: EelFileSystemApi.CreateTemporaryDirectoryOptions,
    ): EelResult<EelPath.Absolute, CreateTemporaryDirectoryError> =
      doCreateTemporaryDirectory(mapper, options)
  }
}

private fun doCreateTemporaryDirectory(
  mapper: EelPathMapper,
  options: EelFileSystemApi.CreateTemporaryDirectoryOptions,
): EelResult<EelPath.Absolute, CreateTemporaryDirectoryError> {
  val dir =
    options.parentDirectory?.let(mapper::toNioPath)?.toFile()
    ?: run {
      val path = Path.of(FileUtilRt.getTempDirectory())
      if (mapper.getOriginalPath(path) != null) {
        return Error(Other(EelPath.Absolute.parse(path.toString(), null), "Can't map this path"))
      }
      path.toFile()
    }
  val tempDirectory = FileUtil.createTempDirectory(
    dir,
    options.prefix,
    options.suffix.nullize(),
    options.deleteOnExit,
  )
  val tempDirectoryEel = mapper.getOriginalPath(tempDirectory.toPath())
  return if (tempDirectoryEel != null)
    Ok(tempDirectoryEel)
  else
    Error(Other(EelPath.Absolute.parse(tempDirectory.toString(), null), "Can't map this path"))
}