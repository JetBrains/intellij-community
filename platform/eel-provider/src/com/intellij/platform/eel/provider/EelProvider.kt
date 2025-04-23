// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.platform.eel.provider.utils.toEelArch
import com.intellij.platform.util.coroutines.forEachConcurrent
import com.intellij.util.system.CpuArch
import com.intellij.util.system.OS
import kotlinx.coroutines.CancellationException
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

interface LocalWindowsEelApi : LocalEelApi, EelWindowsApi
interface LocalPosixEelApi : LocalEelApi, EelPosixApi

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
  return EelNioBridgeService.getInstanceSync().tryGetEelDescriptor(this) ?: LocalEelDescriptor
}

/**
 * Retrieves [EelDescriptor] for the environment where [this] is located.
 * If the project is not the real one (i.e., it is default or not backed by a real file), then [LocalEelDescriptor] will be returned.
 */
fun Project.getEelDescriptor(): EelDescriptor {
  val filePath = projectFilePath
  if (filePath == null) {
    // The path to project file can be null if the project is default or used in tests.
    // While the latter is acceptable, the former can give rise to problems:
    // It is possible to "preconfigure" some settings for projects, such as default SDK or libraries.
    // This preconfiguration appears to be tricky in case of non-local projects: it would require UI changes if we want to configure WSL,
    // and in the case of Docker it is simply impossible to preconfigure a container with UI.
    // So we shall limit this preconfiguration to local projects only, which implies that the default project will be associated with the local eel descriptor.
    return LocalEelDescriptor
  }
  return Path.of(filePath).getEelDescriptor()
}

val localEel: LocalEelApi by lazy {
  if (SystemInfo.isWindows) ApplicationManager.getApplication().service<LocalWindowsEelApi>() else ApplicationManager.getApplication().service<LocalPosixEelApi>()
}

fun EelDescriptor.upgradeBlocking(): EelApi {
  if (this === LocalEelDescriptor) return localEel
  return runBlockingMaybeCancellable { upgrade() }
}

data object LocalEelDescriptor : EelDescriptor {
  private val LOG = logger<LocalEelDescriptor>()

  override val platform: EelPlatform by lazy {
    val arch = CpuArch.CURRENT.toEelArch()

    when {
      SystemInfo.isWindows -> EelPlatform.Windows(arch)
      SystemInfo.isMac -> EelPlatform.Darwin(arch)
      SystemInfo.isLinux -> EelPlatform.Linux(arch)
      SystemInfo.isFreeBSD -> EelPlatform.FreeBSD(arch)
      else -> {
        LOG.info("Eel is not supported on current platform")
        EelPlatform.Linux(arch)
      }
    }
  }

  override suspend fun upgrade(): EelApi {
    return localEel
  }
}

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
    is EelPlatform.FreeBSD -> OS.FreeBSD
  }
}