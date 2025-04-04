// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.local

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.platform.eel.*
import com.intellij.platform.eel.fs.EelFileSystemApi
import com.intellij.platform.eel.fs.EelFileSystemApi.CreateTemporaryEntryError
import com.intellij.platform.eel.fs.LocalEelFileSystemPosixApi
import com.intellij.platform.eel.fs.LocalEelFileSystemWindowsApi
import com.intellij.platform.eel.impl.fs.EelFsResultImpl.Ok
import com.intellij.platform.eel.impl.fs.EelUserPosixInfoImpl
import com.intellij.platform.eel.impl.fs.EelUserWindowsInfoImpl
import com.intellij.platform.eel.impl.fs.PosixNioBasedEelFileSystemApi
import com.intellij.platform.eel.impl.fs.WindowsNioBasedEelFileSystemApi
import com.intellij.platform.eel.impl.local.tunnels.EelLocalTunnelsApiImpl
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.*
import com.intellij.platform.eel.provider.utils.toEelArch
import com.intellij.util.system.CpuArch
import com.intellij.util.text.nullize
import com.sun.security.auth.module.UnixSystem
import org.jetbrains.annotations.VisibleForTesting
import java.io.File
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Path

internal class LocalWindowsEelApiImpl(nioFs: FileSystem = FileSystems.getDefault()) : LocalWindowsEelApi {
  init {
    check(SystemInfo.isWindows)
  }

  override val tunnels: EelTunnelsWindowsApi get() = EelLocalTunnelsApiImpl
  override val descriptor: EelDescriptor get() = LocalEelDescriptor
  override val platform: EelPlatform.Windows get() = EelPlatform.Windows(CpuArch.CURRENT.toEelArch())
  override val exec: EelExecApi = EelLocalExecApi()
  override val userInfo: EelUserWindowsInfo = EelUserWindowsInfoImpl(getLocalUserHome())
  override val archive: EelArchiveApi = LocalEelArchiveApiImpl

  override val fs: LocalEelFileSystemWindowsApi = object : WindowsNioBasedEelFileSystemApi(nioFs, userInfo) {
    override val descriptor: EelDescriptor = LocalEelDescriptor

    override suspend fun createTemporaryDirectory(
      options: EelFileSystemApi.CreateTemporaryEntryOptions,
    ): EelResult<EelPath, CreateTemporaryEntryError> =
      doCreateTemporaryDirectory(options)

    override suspend fun createTemporaryFile(options: EelFileSystemApi.CreateTemporaryEntryOptions): EelResult<EelPath, CreateTemporaryEntryError> {
      return doCreateTemporaryFile(options)
    }
  }
}

@VisibleForTesting
class LocalPosixEelApiImpl(private val nioFs: FileSystem = FileSystems.getDefault()) : LocalPosixEelApi {
  init {
    check(SystemInfo.isUnix)
  }

  override val tunnels: EelTunnelsPosixApi get() = EelLocalTunnelsApiImpl
  override val descriptor: EelDescriptor get() = LocalEelDescriptor
  override val platform: EelPlatform.Posix
    get() {
      val arch = CpuArch.CURRENT.toEelArch()
      return when {
        SystemInfo.isMac -> EelPlatform.Darwin(arch)
        SystemInfo.isLinux -> EelPlatform.Linux(arch)
        SystemInfo.isFreeBSD -> EelPlatform.FreeBSD(arch)
        else -> {
          LOG.info("Eel is not supported on current platform")
          EelPlatform.Linux(arch)
        }
      }
    }

  override val exec: EelExecApi = EelLocalExecApi()
  override val archive: EelArchiveApi = LocalEelArchiveApiImpl

  override val userInfo: EelUserPosixInfo = run {
    val unix = UnixSystem()
    EelUserPosixInfoImpl(uid = unix.uid.toInt(), gid = unix.gid.toInt(), home = getLocalUserHome())
  }

  override val fs: LocalEelFileSystemPosixApi = object : PosixNioBasedEelFileSystemApi(nioFs, userInfo) {
    override val pathOs: EelPath.OS = EelPath.OS.UNIX
    override val descriptor: EelDescriptor get() = LocalEelDescriptor

    override suspend fun createTemporaryDirectory(
      options: EelFileSystemApi.CreateTemporaryEntryOptions,
    ): EelResult<EelPath, CreateTemporaryEntryError> =
      doCreateTemporaryDirectory(options)

    override suspend fun createTemporaryFile(options: EelFileSystemApi.CreateTemporaryEntryOptions): EelResult<EelPath, CreateTemporaryEntryError> {
      return doCreateTemporaryFile(options)
    }
  }
}

private fun doCreateTemporaryDirectory(
  options: EelFileSystemApi.CreateTemporaryEntryOptions,
): EelResult<EelPath, CreateTemporaryEntryError> {
  return doCreateTemporaryEntry(options) { dir, prefix, suffix, deleteOnExit ->
    FileUtil.createTempDirectory(dir, prefix, suffix, deleteOnExit)
  }
}

private fun doCreateTemporaryFile(
  options: EelFileSystemApi.CreateTemporaryEntryOptions,
): EelResult<EelPath, CreateTemporaryEntryError> {
  return doCreateTemporaryEntry(options) { dir, prefix, suffix, deleteOnExit ->
    FileUtil.createTempFile(dir, prefix, suffix, deleteOnExit)
  }
}

private fun doCreateTemporaryEntry(
  options: EelFileSystemApi.CreateTemporaryEntryOptions,
  localCreator: (File, String, String?, Boolean) -> File,
): EelResult<EelPath, CreateTemporaryEntryError> {
  val dir =
    options.parentDirectory?.asNioPathOrNull()?.toFile()
    ?: run {
      val path = Path.of(FileUtilRt.getTempDirectory())
      path.toFile()
    }
  val tempEntry = localCreator(dir, options.prefix, options.suffix.nullize(), options.deleteOnExit)
  val tempDirectoryEel = tempEntry.toPath().asEelPath()
  return Ok(tempDirectoryEel)
}


private val LOG = Logger.getInstance(EelApi::class.java)

private fun getLocalUserHome(): EelPath {
  val homeDirPath = Path.of(System.getProperty("user.home")).toAbsolutePath().toString()
  return checkNotNull(EelPath.parse(homeDirPath, LocalEelDescriptor)) { "Can't parse home dir path: $homeDirPath" }
}