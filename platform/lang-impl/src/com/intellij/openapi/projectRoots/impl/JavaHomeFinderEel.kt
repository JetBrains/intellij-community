// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("JavaHomeFinderEel")

package com.intellij.openapi.projectRoots.impl

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.platform.eel.*
import com.intellij.platform.eel.fs.*
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.utils.awaitProcessResult
import com.intellij.util.suspendingLazy
import kotlinx.coroutines.CoroutineScope
import java.io.IOException
import java.nio.file.Path

private class EelSystemInfoProvider(private val eel: EelApi) : JavaHomeFinder.SystemInfoProvider() {
  @Service
  private class ScopeService(coroutineScope: CoroutineScope) : CoroutineScope by coroutineScope

  private val environmentVariables = service<ScopeService>().suspendingLazy {
    eel.exec.fetchLoginShellEnvVariables()
  }

  override fun getEnvironmentVariable(name: String): String? = runBlockingMaybeCancellable {
    environmentVariables.getValue()[name]
  }

  override fun getPath(path: String, vararg more: String): Path =
    eel.mapper.toNioPath(EelPath.Absolute.parse(eel.fs.pathOs, path, *more))

  override fun getUserHome(): Path? = with(eel) {
    mapper.toNioPath(fs.user.home)
  }

  override fun getFsRoots(): Collection<Path> = runBlockingMaybeCancellable {
    val paths = when (val fs = eel.fs) {
      is EelFileSystemPosixApi -> listOf(EelPath.Absolute.build("/"))
      is EelFileSystemWindowsApi -> fs.getRootDirectories()
      else -> error(fs)
    }
    paths.map(eel.mapper::toNioPath)
  }

  override fun getPathSeparator(): String? =
    when (val fs = eel.fs) {
      is EelFileSystemPosixApi -> ":"
      is EelFileSystemWindowsApi -> ";"
      else -> error(fs)
    }

  private val isCaseSensitive = service<ScopeService>().suspendingLazy {
    val testDir = eel.fs.user.home
    val type = when (val stat = eel.fs.stat(testDir, EelFileSystemApi.SymlinkPolicy.RESOLVE_AND_FOLLOW)) {
      is EelResult.Ok -> stat.value.type
      is EelResult.Error -> error(stat)
    }

    val sensitivity = when (val type = type) {
      is EelFileInfo.Type.Directory -> type.sensitivity
      is EelFileInfo.Type.Other, is EelFileInfo.Type.Regular, is EelPosixFileInfo.Type.Symlink -> error("Not a directory")
    }

    when (sensitivity) {
      EelFileInfo.CaseSensitivity.SENSITIVE -> true
      EelFileInfo.CaseSensitivity.INSENSITIVE -> false
      EelFileInfo.CaseSensitivity.UNKNOWN -> true
    }
  }

  override fun isFileSystemCaseSensitive(): Boolean = runBlockingMaybeCancellable {
    isCaseSensitive.getValue()
  }
}

internal fun javaHomeFinderEel(eel: EelApi): JavaHomeFinderBasic {
  val systemInfoProvider = EelSystemInfoProvider(eel)

  val parentFinder = when (eel.platform) {
    is EelPlatform.Windows ->
      JavaHomeFinderWindows(
        registeredJdks = true,
        wslJdks = false,
        systemInfoProvider = systemInfoProvider,
        processRunner = { cmd ->
          runBlockingMaybeCancellable {
            // TODO Introduce Windows Registry access in EelApi
            val process = eel.exec.execute(EelExecApi.ExecuteProcessOptions.Builder(cmd.first()).args(cmd.drop(1)).build()).getOrThrow {
              throw IOException("Failed to read Windows Registry: $it")
            }
            val result = process.awaitProcessResult()
            if (result.exitCode != 0) throw IOException("Failed to read Windows Registry: $result")
            result.stdout
          }
        }
      )

    is EelPlatform.Darwin -> JavaHomeFinderMac(systemInfoProvider)

    is EelPlatform.Linux -> {
      val checkPaths = JavaHomeFinder.DEFAULT_JAVA_LINUX_PATHS.toMutableSet()
      val userHome = eel.fs.user.home
      checkPaths.add(eel.mapper.toNioPath(userHome.resolve(EelPath.Relative.build(".jdks"))).toString())
      JavaHomeFinderBasic(systemInfoProvider).checkSpecifiedPaths(*checkPaths.toTypedArray())
    }

    is EelPlatform.Posix -> JavaHomeFinderBasic(systemInfoProvider)
  }

  val isLocal = eel is LocalEelApi
  return parentFinder
    .checkDefaultInstallDir(isLocal)
    .checkUsedInstallDirs(isLocal)
    .checkConfiguredJdks(isLocal)
}