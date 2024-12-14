// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl.ijent.nio.toggle

import com.intellij.diagnostic.VMOptions
import com.intellij.execution.eel.EelApiWithPathsMapping
import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.WslDistributionManager
import com.intellij.execution.wsl.WslIjentAvailabilityService
import com.intellij.execution.wsl.WslIjentManager
import com.intellij.execution.wsl.WslPath
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.platform.core.nio.fs.MultiRoutingFileSystemProvider
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.provider.EelDescriptor
import com.intellij.platform.eel.provider.EelProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.io.BufferedReader
import java.nio.file.FileSystems
import java.nio.file.Path
import kotlin.io.path.bufferedReader
import kotlin.io.path.isSameFileAs

/**
 * This service, along with listeners inside it, enables and disables access to WSL drives through IJent.
 */
@Internal
@Service
@VisibleForTesting
class IjentWslNioFsToggler(private val coroutineScope: CoroutineScope) {
  companion object {
    suspend fun instanceAsync(): IjentWslNioFsToggler = serviceAsync()
    fun instance(): IjentWslNioFsToggler = service()
  }

  val isAvailable: Boolean get() = strategy != null

  fun enableForAllWslDistributions() {
    logErrorIfNotWindows()
    strategy?.enableForAllWslDistributions()
  }

  @TestOnly
  fun switchToIjentFs(distro: WSLDistribution) {
    logErrorIfNotWindows()
    strategy ?: error("Not available")
    strategy.switchToIjentFs(distro)
  }

  @TestOnly
  fun switchToTracingWsl9pFs(distro: WSLDistribution) {
    logErrorIfNotWindows()
    strategy ?: error("Not available")
    strategy.switchToTracingWsl9pFs(distro)
  }

  @TestOnly
  fun unregisterAll() {
    logErrorIfNotWindows()
    strategy ?: error("Not available")
    strategy.unregisterAll()
  }

  private fun logErrorIfNotWindows() {
    if (!SystemInfo.isWindows) {
      thisLogger().error("${javaClass.name} should be requested only on Windows")
    }
  }

  // TODO Move to ijent.impl?
  internal class WslEelProvider : EelProvider {
    override suspend fun getEelApi(path: Path): EelApi? {
      val distribution = WslDistributionManager.getInstance().installedDistributions.firstOrNull { distro -> distro.getUNCRootPath().toString().compareTo(path.root.toString(), true) == 0 }
      if (distribution == null) {
        return null
      }
      return try {
        getApiByDistribution(distribution)
      }
      catch (_: IllegalStateException) {
        return null
      }
    }

    suspend fun getApiByDistribution(distro: WSLDistribution): EelApi {
      val enabledDistros = serviceAsync<IjentWslNioFsToggler>().strategy?.enabledInDistros
      if (enabledDistros == null || distro !in enabledDistros) {
        throw IllegalStateException("IJent is not enabled in $distro")
      }
      return EelApiWithPathsMapping(ephemeralRoot = distro.getUNCRootPath(), WslIjentManager.getInstance().getIjentApi(distro, null, rootUser = false))
    }

    override fun getEelDescriptor(path: Path): EelDescriptor? =
      service<IjentWslNioFsToggler>().strategy?.enabledInDistros
        ?.find { distro -> path.root != null && distro.getUNCRootPath().isSameFileAs(path.root) }
        ?.let { WslEelDescriptor(it, this@WslEelProvider) }

    /**
     * Starts the IJent if a project on WSL is opened.
     *
     * At the moment of writing this string,
     * this class was just an optimization handler that speeds up sometimes the first request to the IJent.
     * It was not necessary for running the IDE.
     */
    override suspend fun tryInitialize(project: Project) = tryInitializeEelOnWsl(project)

    private data class WslEelDescriptor(val distribution: WSLDistribution, val provider: WslEelProvider) : EelDescriptor {
      override suspend fun upgrade(): EelApi {
        return provider.getApiByDistribution(distribution)
      }

      override fun equals(other: Any?): Boolean {
        return other is WslEelDescriptor && other.distribution.id == distribution.id
      }

      override fun hashCode(): Int {
        return distribution.id.hashCode()
      }
    }
  }

  private val strategy = run {
    val defaultProvider = FileSystems.getDefault().provider()
    when {
      !WslIjentAvailabilityService.getInstance().useIjentForWslNioFileSystem() -> null

      defaultProvider.javaClass.name == MultiRoutingFileSystemProvider::class.java.name -> {
        IjentWslNioFsToggleStrategy(defaultProvider, coroutineScope)
      }

      else -> {
        val vmOptions = runCatching {
          VMOptions.getUserOptionsFile()?.bufferedReader()?.use<BufferedReader, String> { it.readText() }
          ?: "<null>"
        }.getOrElse<String, String> { err -> err.stackTraceToString() }

        val systemProperties = runCatching {
          System.getProperties().entries.joinToString("\n") { (k, v) -> "$k=$v" }
        }.getOrElse<String, String> { err -> err.stackTraceToString() }

        val message = "The default filesystem ${FileSystems.getDefault()} is not ${MultiRoutingFileSystemProvider::class.java}"

        if (ApplicationManager.getApplication().isUnitTestMode) {
          logger<IjentWslNioFsToggler>().warn("$message\nVM Options:\n$vmOptions\nSystem properties:\n$systemProperties")
        }
        else {
          logger<IjentWslNioFsToggler>().error(
            message,
            Attachment("user vmOptions.txt", vmOptions),
            Attachment("system properties.txt", systemProperties),
          )
        }
        null
      }
    }
  }
}

private suspend fun tryInitializeEelOnWsl(project: Project) = coroutineScope {
  if (project.isDefault) {
    return@coroutineScope
  }

  if (!WslIjentAvailabilityService.getInstance().useIjentForWslNioFileSystem()) {
    return@coroutineScope
  }

  val ijentWslNioFsToggler = IjentWslNioFsToggler.instanceAsync()
  if (!ijentWslNioFsToggler.isAvailable) {
    return@coroutineScope
  }

  val projectFile = project.projectFilePath
  check(projectFile != null) { "Impossible: project is not default, but it does not have project file" }

  if (!WslPath.isWslUncPath(projectFile)) {
    return@coroutineScope
  }

  launch {
    ijentWslNioFsToggler.enableForAllWslDistributions()
  }

  val allWslDistributions = async(Dispatchers.IO) {
    serviceAsync<WslDistributionManager>().installedDistributions
  }

  val path = Path.of(projectFile)
  for (distro in allWslDistributions.await()) {
    val matches =
      try {
        distro.getWslPath(path) != null
      }
      catch (_: IllegalArgumentException) {
        false
      }
    if (matches) {
      launch {
        serviceAsync<WslIjentManager>().getIjentApi(distro, project, false)
      }
    }
  }
}
