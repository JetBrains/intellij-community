// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("JavaHomeFinderEel")

package com.intellij.openapi.projectRoots.impl

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.platform.eel.*
import com.intellij.platform.eel.fs.*
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.upgradeBlocking
import com.intellij.platform.eel.provider.utils.awaitProcessResult
import com.intellij.platform.eel.provider.utils.stdoutString
import com.intellij.util.suspendingLazy
import kotlinx.coroutines.CoroutineScope
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
    more.fold(EelPath.parse(path, eel.descriptor), EelPath::resolve).asNioPath()

  override fun getUserHome(): Path? = with(eel) {
    fs.user.home.asNioPath()
  }

  override fun getFsRoots(): Collection<Path> = runBlockingMaybeCancellable {
    val paths = when (val fs = eel.fs) {
      is EelFileSystemPosixApi -> listOf(EelPath.build(listOf("/"), eel.descriptor))
      is EelFileSystemWindowsApi -> fs.getRootDirectories()
      else -> error(fs)
    }
    paths.map { it.asNioPath() }
  }

  override fun getPathSeparator(): String? =
    when (val fs = eel.fs) {
      is EelFileSystemPosixApi -> ":"
      is EelFileSystemWindowsApi -> ";"
      else -> error(fs)
    }

  private val isCaseSensitive = service<ScopeService>().suspendingLazy {
    val testDir = eel.fs.user.home
    val type = when (val stat = eel.fs.stat(testDir).resolveAndFollow().eelIt()) {
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

internal fun javaHomeFinderEel(descriptor: EelDescriptor): JavaHomeFinderBasic {
  val eel = descriptor.upgradeBlocking()
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
            val process = try {
              eel.exec.spawnProcess(cmd.first()).args(cmd.drop(1)).eelIt()
            } catch (_ : EelExecApi.ExecuteProcessException) {
              // registry reading can fail, in this case we return no output just like `com.intellij.openapi.util.io.WindowsRegistryUtil.readRegistry`
              return@runBlockingMaybeCancellable ""
            }
            val result = process.awaitProcessResult()
            if (result.exitCode != 0) {
              return@runBlockingMaybeCancellable ""
            }
            result.stdoutString
          }
        }
      )

    is EelPlatform.Darwin -> JavaHomeFinderMac(systemInfoProvider)

    is EelPlatform.Linux -> {
      val checkPaths = JavaHomeFinder.DEFAULT_JAVA_LINUX_PATHS.map {
        EelPath.parse(it, descriptor).asNioPath().toString()
      }.toMutableSet()
      val userHome = eel.fs.user.home
      checkPaths.add(userHome.resolve(".jdks").asNioPath().toString())
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