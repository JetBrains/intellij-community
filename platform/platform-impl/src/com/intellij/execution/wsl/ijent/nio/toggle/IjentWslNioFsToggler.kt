// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl.ijent.nio.toggle

import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.WslIjentAvailabilityService
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.SystemInfo
import com.intellij.platform.core.nio.fs.MultiRoutingFileSystemProvider
import com.intellij.platform.ijent.IjentId
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.nio.file.FileSystems

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

  init {
    if (!SystemInfo.isWindows) {
      thisLogger().error("${javaClass.name} should be requested only on Windows")
    }
  }

  val isAvailable: Boolean get() = strategy != null

  suspend fun enableForAllWslDistributions() {
    strategy ?: error("Not available")
    strategy.enableForAllWslDistributions()
  }

  @TestOnly
  fun switchToIjentFs(distro: WSLDistribution, ijentId: IjentId) {
    strategy ?: error("Not available")
    strategy.switchToIjentFs(distro, ijentId)
  }

  @TestOnly
  fun switchToTracingWsl9pFs(distro: WSLDistribution) {
    strategy ?: error("Not available")
    strategy.switchToTracingWsl9pFs(distro)
  }

  @TestOnly
  fun unregisterAll() {
    strategy ?: error("Not available")
    strategy.unregisterAll()
  }

  private val strategy = run {
    val defaultProvider = FileSystems.getDefault().provider()
    when {
      !WslIjentAvailabilityService.getInstance().useIjentForWslNioFileSystem() -> null

      defaultProvider.javaClass.name == MultiRoutingFileSystemProvider::class.java.name -> {
        IjentWslNioFsToggleStrategy(defaultProvider, coroutineScope)
      }

      else -> {
        logger<IjentWslNioFsToggler>().warn(
          "The default filesystem ${FileSystems.getDefault()} is not ${MultiRoutingFileSystemProvider::class.java}"
        )
        null
      }
    }
  }
}