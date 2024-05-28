// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl.ijent.nio.toggle

import com.intellij.diagnostic.VMOptions
import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.WslIjentAvailabilityService
import com.intellij.idea.AppMode
import com.intellij.openapi.application.*
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.SystemInfo
import com.intellij.platform.core.nio.fs.CoreBootstrapSecurityManager
import com.intellij.platform.core.nio.fs.MultiRoutingFileSystemProvider
import com.intellij.platform.ijent.IjentId
import kotlinx.coroutines.*
import java.io.IOException
import java.nio.file.FileSystems

/**
 * This service, along with listeners inside it, enables and disables access to WSL drives through IJent.
 */
@Service
internal class IjentWslNioFsToggler(internal val coroutineScope: CoroutineScope) {
  companion object {
    suspend fun instanceAsync(): IjentWslNioFsToggler = serviceAsync()
    fun instance(): IjentWslNioFsToggler = service()
  }

  init {
    if (!SystemInfo.isWindows) {
      thisLogger().error("${javaClass.name} should be requested only on Windows")
    }
  }

  fun ensureInVmOptions(): List<Pair<String, String?>> {
    val options: List<Triple<String, String, String?>> = listOf(
      Triple(
        "-Djava.nio.file.spi.DefaultFileSystemProvider=",
        MultiRoutingFileSystemProvider::class.java.name,
        null,
      ),
      Triple(
        "-Xbootclasspath/a:out/classes/production/intellij.platform.core.nio.fs",
        "",
        null,
      ),
      Triple(
        "-Djava.security.manager=",
        CoreBootstrapSecurityManager::class.java.name,
        null,
      ),
      Triple(
        "-Didea.io.use.nio2=",
        "true",
        null,
      ),
    )

    val changedOptions = mutableListOf<Pair<String, String?>>()
    val isEnabled = WslIjentAvailabilityService.Companion.getInstance().useIjentForWslNioFileSystem()

    for ((name, valueForEnabled, valueForDisabled) in options) {
      val value = if (isEnabled) valueForEnabled else valueForDisabled
      // TODO Explain why there's a difference in Dev Mode.
      if (VMOptions.readOption(name, AppMode.isDevServer() || ApplicationManager.getApplication().isUnitTestMode) != value) {
        changedOptions += name to value
        try {
          VMOptions.setOption(name, value)
        }
        catch (err: IOException) {
          if (!ApplicationManager.getApplication().isUnitTestMode) {
            throw err
          }
        }
      }
    }

    return changedOptions
  }

  fun enable(distro: WSLDistribution, ijentId: IjentId) {
    strategy.enable(distro, ijentId)
  }

  // TODO Disable when IJent exits.
  fun disable(distro: WSLDistribution) {
    strategy.disable(distro)
  }

  private val strategy = run {
    val defaultProvider = FileSystems.getDefault().provider()
    when {
      !WslIjentAvailabilityService.Companion.getInstance().useIjentForWslNioFileSystem() -> FallbackIjentWslNioFsToggleStrategy

      defaultProvider.javaClass.name == MultiRoutingFileSystemProvider::class.java.name -> {
        DefaultIjentWslNioFsToggleStrategy(defaultProvider, coroutineScope)
      }

      else -> {
        logger<IjentWslNioFsToggler>().warn(
          "The default filesystem ${FileSystems.getDefault()} is not ${MultiRoutingFileSystemProvider::class.java}"
        )
        FallbackIjentWslNioFsToggleStrategy
      }
    }
  }

  init {
    strategy.initialize()
  }

  val isInitialized: Boolean get() = strategy.isInitialized
}