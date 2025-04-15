// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl.ijent.nio.toggle

import com.intellij.diagnostic.VMOptions
import com.intellij.execution.wsl.*
import com.intellij.execution.wsl.ijent.nio.toggle.IjentWslNioFsToggler.WslEelProvider
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.SystemInfo
import com.intellij.platform.core.nio.fs.MultiRoutingFileSystemProvider
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.EelProvider
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.io.BufferedReader
import java.nio.file.FileSystems
import java.nio.file.Path
import kotlin.io.path.bufferedReader

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
    strategy.enabledInDistros.add(distro)
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

    suspend fun getApiByDistribution(distro: WSLDistribution): EelApi {
      val enabledDistros = serviceAsync<IjentWslNioFsToggler>().strategy?.enabledInDistros
      if (enabledDistros == null || distro !in enabledDistros) {
        throw IllegalStateException("IJent is not enabled in $distro")
      }
      return WslIjentManager.getInstance().getIjentApi(distro, null, rootUser = false)
    }

    override suspend fun tryInitialize(path: String) = tryInitializeEelOnWsl(path)
  }

  private val strategy = run {
    val defaultProvider = FileSystems.getDefault().provider()
    when {
      !WslIjentAvailabilityService.getInstance().useIjentForWslNioFileSystem() -> null

      defaultProvider.javaClass.name == MultiRoutingFileSystemProvider::class.java.name -> {
        IjentWslNioFsToggleStrategy(coroutineScope)
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

        logger<IjentWslNioFsToggler>().warn("$message\nVM Options:\n$vmOptions\nSystem properties:\n$systemProperties")
        null
      }
    }
  }
}


private suspend fun tryInitializeEelOnWsl(path: String) {
  if (!WslIjentAvailabilityService.getInstance().useIjentForWslNioFileSystem()) {
    return
  }

  if (!WslPath.isWslUncPath(path)) {
    return
  }

  val ijentWslNioFsToggler = IjentWslNioFsToggler.instanceAsync()

  coroutineScope {
    launch {
      ijentWslNioFsToggler.enableForAllWslDistributions()
    }

    val allWslDistributions = async(Dispatchers.IO) {
      serviceAsync<WslDistributionManager>().installedDistributions
    }

    val path = Path.of(path)

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
          serviceAsync<WslIjentManager>().getIjentApi(distro, null, false)
        }
      }
    }
  }
}


data class WslEelDescriptor(val distribution: WSLDistribution) : EelDescriptor {
  override val operatingSystem: EelPath.OS = EelPath.OS.UNIX


  override suspend fun upgrade(): EelApi {
    return WslEelProvider().getApiByDistribution(distribution)
  }

  override fun equals(other: Any?): Boolean {
    return other is WslEelDescriptor && other.distribution.id == distribution.id
  }

  override fun hashCode(): Int {
    return distribution.id.hashCode()
  }
}