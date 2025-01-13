// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("EelProviderUtil")

package com.intellij.platform.eel.provider

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.platform.eel.*
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.util.coroutines.forEachConcurrent
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import com.intellij.util.system.OS
import kotlinx.coroutines.CancellationException
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

interface LocalWindowsEelApi : LocalEelApi, EelWindowsApi
interface LocalPosixEelApi : LocalEelApi, EelPosixApi

suspend fun Path.getEelApi(): EelApi {
  return getEelDescriptor().upgrade()
}

object EelInitialization {
  private val logger = logger<EelInitialization>()

  suspend fun runEelInitialization(path: String) {
    val eels = EelProvider.EP_NAME.extensionList
    eels.forEachConcurrent { eelProvider ->
      try {
        eelProvider.tryInitialize(path)
      }
      catch (e: CancellationException) {
        throw e
      }
      catch (e: Throwable) {
        logger.error(e)
      }
    }
  }

  suspend fun runEelInitialization(project: Project) {
    if (project.isDefault) {
      return
    }

    val projectFile = project.projectFilePath
    check(projectFile != null) { "Impossible: project is not default, but it does not have project file" }

    runEelInitialization(projectFile)
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

fun EelDescriptor.upgradeBlocking(): EelApi {
  return runBlockingMaybeCancellable { upgrade() }
}

@RequiresBlockingContext
fun Path.getEelApiBlocking(): EelApi = runBlockingMaybeCancellable { getEelApi() }

@ApiStatus.Internal
interface EelProvider {
  companion object {
    val EP_NAME: ExtensionPointName<EelProvider> = ExtensionPointName<EelProvider>("com.intellij.eelProvider")
  }

  /**
   * Runs an initialization process for [EelApi] relevant to [path] during the process of its opening.
   *
   * This function runs **early**, so implementors need to be careful with performance.
   * This function is called for every opening [Project],
   * so the implementation is expected to exit quickly if it decides that it is not responsible for [path].
   */
  suspend fun tryInitialize(path: String)
}

fun EelApi.systemOs(): OS {
  return when (platform) {
    is EelPlatform.Linux -> OS.Linux
    is EelPlatform.Darwin -> OS.macOS
    is EelPlatform.Windows -> OS.Windows
  }
}