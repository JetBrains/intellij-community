// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.provider.utils

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EnvironmentVariablesOptionsBuilder
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.toEelApiBlocking
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

@ApiStatus.Experimental
object EelSystemFolderUtils {
  @JvmStatic
  suspend fun getCommonDataFolder(descriptor: EelDescriptor): Path {
    if (descriptor === LocalEelDescriptor) return PathManager.getCommonDataPath()

    return getCommonDataFolder(descriptor.toEelApiBlocking())
  }

  @JvmStatic
  suspend fun getCommonDataFolder(eel: EelApi): Path {
    if (eel.descriptor === LocalEelDescriptor) return PathManager.getCommonDataPath()

    // Compose the path in the host's own form: `getDefaultCommonDataPathFor` may return a raw environment value
    // (XDG_DATA_HOME, APPDATA), which is a host-native string that `Path.of` would misroute to the local file system.
    val options = EnvironmentVariablesOptionsBuilder().loginInteractiveViaShell().build()
    val envs = withContext(Dispatchers.IO) { eel.exec.environmentVariables(options).await() }
    val remotePath = PathManager.getDefaultCommonDataPathFor(
      eel.platform.toOs(),
      eel.userInfo.home.toString(),
      envs,
    )
    return EelPath.parse(remotePath, eel.descriptor).asNioPath()
  }

  @JvmStatic
  @RequiresBackgroundThread(generateAssertion = false)
  fun getSystemFolder(project: Project): Path = getSystemFolder(project.getEelDescriptor().toEelApiBlocking())

  @JvmStatic
  @RequiresBackgroundThread(generateAssertion = false)
  fun getSystemFolder(descriptor: EelDescriptor): Path = getSystemFolder(descriptor.toEelApiBlocking())

  @JvmStatic
  @RequiresBackgroundThread(generateAssertion = false)
  fun getSystemFolder(eel: EelApi): Path {
    if (eel.descriptor === LocalEelDescriptor) return PathManager.getSystemDir()

    val selector = PathManager.getPathsSelector() ?: "IJ-Platform"
    val systemPath = PathManager.getDefaultSystemPathFor(
      eel.platform.toOs(),
      eel.userInfo.home.toString(),
      selector,
      eel.exec.fetchLoginShellEnvVariablesBlocking()
    )
    return EelPath.parse(systemPath, eel.descriptor).asNioPath()
  }
}
