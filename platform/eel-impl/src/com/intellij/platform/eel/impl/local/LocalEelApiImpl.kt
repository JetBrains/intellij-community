// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.local

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.platform.eel.*
import com.intellij.platform.eel.fs.EelFileSystemApi
import com.intellij.platform.eel.fs.EelFileSystemApi.CreateTemporaryEntryError
import com.intellij.platform.eel.fs.EelFileSystemPosixApi
import com.intellij.platform.eel.fs.EelFileSystemWindowsApi
import com.intellij.platform.eel.fs.getPath
import com.intellij.platform.eel.impl.fs.*
import com.intellij.platform.eel.impl.fs.EelFsResultImpl.Ok
import com.intellij.platform.eel.impl.fs.EelFsResultImpl.Other
import com.intellij.platform.eel.impl.local.tunnels.EelLocalTunnelsPosixApi
import com.intellij.platform.eel.impl.local.tunnels.EelLocalTunnelsWindowsApi
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.path.pathOs
import com.intellij.platform.eel.provider.LocalPosixEelApi
import com.intellij.platform.eel.provider.LocalWindowsEelApi
import com.intellij.util.text.nullize
import com.sun.security.auth.module.UnixSystem
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.VisibleForTesting
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Path

internal class LocalEelPathMapper(private val eelApi: EelApi) : EelPathMapper {
  override fun getOriginalPath(path: Path): EelPath {
    return eelApi.fs.getPath(path.toString(), eelApi.platform.pathOs)
  }

  override suspend fun maybeUploadPath(path: Path, scope: CoroutineScope, options: EelFileSystemApi.CreateTemporaryEntryOptions): EelPath {
    return getOriginalPath(path)
  }

  override fun toNioPath(path: EelPath): Path {
    return Path.of(path.toString())
  }

  override fun pathPrefix(): String {
    return ""
  }
}

internal class LocalWindowsEelApiImpl(private val nioFs: FileSystem = FileSystems.getDefault()) : LocalWindowsEelApi {
  init {
    check(SystemInfo.isWindows)
  }

  override val tunnels: EelTunnelsWindowsApi get() = EelLocalTunnelsWindowsApi
  override val platform: EelPlatform.Windows get() = if (SystemInfo.isAarch64) EelPlatform.Arm64Windows else EelPlatform.X64Windows
  override val exec: EelExecApi = EelLocalExecApi()
  override val userInfo: EelUserWindowsInfo = EelUserWindowsInfoImpl(getLocalUserHome(EelPath.OS.WINDOWS))
  override val mapper: EelPathMapper = LocalEelPathMapper(this)
  override val archive: EelArchiveApi = LocalEelArchiveApiImpl

  override val fs: EelFileSystemWindowsApi = object : WindowsNioBasedEelFileSystemApi(nioFs, userInfo) {
    override fun toNioFs(): FileSystem = nioFs

    override suspend fun createTemporaryDirectory(
      options: EelFileSystemApi.CreateTemporaryEntryOptions,
    ): EelResult<EelPath, CreateTemporaryEntryError> =
      doCreateTemporaryDirectory(mapper, options)
  }
}

@VisibleForTesting
class LocalPosixEelApiImpl(private val nioFs: FileSystem = FileSystems.getDefault()) : LocalPosixEelApi {
  init {
    check(SystemInfo.isUnix)
  }

  override val tunnels: EelTunnelsPosixApi get() = EelLocalTunnelsPosixApi
  override val platform: EelPlatform.Posix = if (SystemInfo.isMac) {
    if (SystemInfo.isAarch64) {
      EelPlatform.Arm64Darwin
    }
    else {
      EelPlatform.X8664Darwin
    }
  }
  else if (SystemInfo.isLinux) {
    if (SystemInfo.isAarch64) {
      EelPlatform.Aarch64Linux
    }
    else {
      EelPlatform.X8664Linux
    }
  }
  else {
    LOG.info("Eel is not supported on current platform")
    EelPlatform.X8664Linux
  }
  override val exec: EelExecApi = EelLocalExecApi()
  override val mapper: EelPathMapper = LocalEelPathMapper(this)
  override val archive: EelArchiveApi = LocalEelArchiveApiImpl

  override val userInfo: EelUserPosixInfo = run {
    val unix = UnixSystem()
    EelUserPosixInfoImpl(uid = unix.uid.toInt(), gid = unix.gid.toInt(), home = getLocalUserHome(EelPath.OS.UNIX))
  }

  override val fs: EelFileSystemPosixApi = object : PosixNioBasedEelFileSystemApi(nioFs, userInfo) {
    override val pathOs: EelPath.OS = EelPath.OS.UNIX
    override fun toNioFs(): FileSystem  = nioFs

    override suspend fun createTemporaryDirectory(
      options: EelFileSystemApi.CreateTemporaryEntryOptions,
    ): EelResult<EelPath, CreateTemporaryEntryError> =
      doCreateTemporaryDirectory(mapper, options)
  }
}

private fun doCreateTemporaryDirectory(
  mapper: EelPathMapper,
  options: EelFileSystemApi.CreateTemporaryEntryOptions,
): EelResult<EelPath, CreateTemporaryEntryError> {
  val dir =
    options.parentDirectory?.let(mapper::toNioPath)?.toFile()
    ?: run {
      val path = Path.of(FileUtilRt.getTempDirectory())
      if (mapper.getOriginalPath(path) == null) {
        return EelFsResultImpl.Error(Other(EelPath.parse(path.toString(), null), "Can't map this path"))
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
    EelFsResultImpl.Error(Other(EelPath.parse(tempDirectory.toString(), null), "Can't map this path"))
}

private val LOG = Logger.getInstance(EelApi::class.java)

private fun getLocalUserHome(os: EelPath.OS): EelPath {
  val homeDirPath = Path.of(System.getProperty("user.home")).toAbsolutePath().toString()
  return checkNotNull(EelPath.parse(homeDirPath, os)) { "Can't parse home dir path: $homeDirPath" }
}