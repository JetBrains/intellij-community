// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("EelProviderUtil")

package com.intellij.platform.eel.provider

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.platform.eel.*
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.util.coroutines.forEachConcurrent
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import com.intellij.util.system.OS
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

interface LocalWindowsEelApi : LocalEelApi, EelWindowsApi
interface LocalPosixEelApi : LocalEelApi, EelPosixApi

suspend fun Path.getEelApi(): EelApi {
  return getEelDescriptor().upgrade()
}

object EelInitialization {
  suspend fun runEelInitialization(project: Project) {
    val eels = EelProvider.EP_NAME.extensionList
    eels.forEachConcurrent { eelProvider ->
      eelProvider.tryInitialize(project)
    }
  }
}

fun Path.getEelDescriptor(): EelDescriptor {
  return ApplicationManager.getApplication().service<EelNioBridgeService>().tryGetEelDescriptor(this) ?: LocalEelDescriptor
}

val localEel: LocalEelApi by lazy {
  if (SystemInfo.isWindows) ApplicationManager.getApplication().service<LocalWindowsEelApi>() else ApplicationManager.getApplication().service<LocalPosixEelApi>()
}

data object LocalEelDescriptor : EelDescriptor {
  override val operatingSystem: EelPath.OS
    get() = if (SystemInfo.isWindows) {
      EelPath.OS.WINDOWS
    }
    else {
      EelPath.OS.UNIX
    }

  override suspend fun upgrade(): EelApi {
    return localEel
  }
}

@RequiresBlockingContext
fun Path.getEelApiBlocking(): EelApi = runBlockingMaybeCancellable { getEelApi() }

@ApiStatus.Internal
interface EelProvider {
  companion object {
    val EP_NAME: ExtensionPointName<EelProvider> = ExtensionPointName<EelProvider>("com.intellij.eelProvider")
  }
  /**
   * Runs an initialization process for [EelApi] relevant to [project] during the process of its opening.
   *
   * This function runs **early**, so implementors need to be careful with performance.
   * This function is called for every opening [Project],
   * so the implementation is expected to exit quickly if it decides that it is not responsible for [project].
   */
  suspend fun tryInitialize(project: Project)
}

fun EelApi.systemOs(): OS {
  return when (platform) {
    is EelPlatform.Linux -> OS.Linux
    is EelPlatform.Darwin -> OS.macOS
    is EelPlatform.Windows -> OS.Windows
  }
}