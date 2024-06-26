// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl.ijent.nio.toggle

import com.intellij.diagnostic.VMOptions
import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.WslIjentAvailabilityService
import com.intellij.idea.AppMode
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.SystemInfo
import com.intellij.platform.core.nio.fs.CoreBootstrapSecurityManager
import com.intellij.platform.core.nio.fs.MultiRoutingFileSystemProvider
import com.intellij.platform.ijent.IjentId
import com.intellij.platform.ijent.community.buildConstants.IJENT_BOOT_CLASSPATH_MODULE
import com.intellij.platform.ijent.community.buildConstants.IJENT_WSL_FILE_SYSTEM_REGISTRY_KEY
import com.intellij.platform.ijent.community.buildConstants.isIjentWslFsEnabledByDefaultForProduct
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.VisibleForTesting
import java.io.IOException
import java.nio.file.FileSystems

/**
 * This service, along with listeners inside it, enables and disables access to WSL drives through IJent.
 */
@Internal
@Service
@VisibleForTesting
class IjentWslNioFsToggler(@VisibleForTesting val coroutineScope: CoroutineScope) { // TODO Try to hide coroutineScope
  companion object {
    suspend fun instanceAsync(): IjentWslNioFsToggler = serviceAsync()
    fun instance(): IjentWslNioFsToggler = service()

    @VisibleForTesting
    fun ensureInVmOptionsImpl(
      isEnabled: Boolean,
      forceProductionOptions: Boolean,
      isEnabledByDefault: Boolean = isIjentWslFsEnabledByDefaultForProduct(ApplicationNamesInfo.getInstance().scriptName),
      getOptionByPrefix: (String) -> String?,
    ): Collection<Pair<String, String?>> {
      val changedOptions = mutableListOf<Pair<String, String?>>()

      run {
        val prefix = "-D${IJENT_WSL_FILE_SYSTEM_REGISTRY_KEY}="
        val actualValue = getOptionByPrefix(prefix)?.toBooleanStrictOrNull() ?: false
        if (actualValue != isEnabled || isEnabledByDefault && !isEnabled) {
          changedOptions += prefix to isEnabled.toString()
        }
      }

      var forceDefaultFs = false
      run {
        val prefix = "-Djava.nio.file.spi.DefaultFileSystemProvider="
        val actualValue = getOptionByPrefix(prefix)

        if (isEnabled) {
          if (actualValue != MultiRoutingFileSystemProvider::class.java.name) {
            changedOptions += prefix to MultiRoutingFileSystemProvider::class.java.name
          }
        }
        else if (actualValue == MultiRoutingFileSystemProvider::class.java.name) {
          // sun.nio.fs.WindowsFileSystemProvider doesn't have the constructor required for SPI.
          // Also, it's not always possible to remove a VM option.
          // Therefore, this special flag orders MultiRoutingFileSystemProvider to delegate everything to the default FS.
          forceDefaultFs = true
        }
      }

      //see idea/nativeHelpers/buildTypes/ijent/performance/IJentWslBenchmarkTests.kt:38
      if (!forceProductionOptions && isEnabled && ApplicationManager.getApplication().isUnitTestMode) {
        val prefix = "-Xbootclasspath/a:out/tests/classes/production/$IJENT_BOOT_CLASSPATH_MODULE"
        val actualValue = getOptionByPrefix(prefix)

        if (actualValue != "") {
          changedOptions += prefix to ""
        }
      }

      run {
        val prefix = "-Djava.security.manager="
        val actualValue = getOptionByPrefix(prefix)

        if (isEnabled) {
          if (actualValue != CoreBootstrapSecurityManager::class.java.name) {
            changedOptions += prefix to CoreBootstrapSecurityManager::class.java.name
          }
        }
        else {
          // It's not always possible to remove a VM Option (if an option is defined in a product-level vmoptions file).
          // However, CoreBootstrapSecurityManager does nothing potentially harmful.
          // The option is kept as is.
        }
      }

      run {
        val prefix = "-Didea.force.default.filesystem="
        val actualValue = getOptionByPrefix(prefix)

        if (isEnabled) {
          if (actualValue != null && actualValue != "false") {
            // It's not always possible to remove a VM Option (if an option is defined in a product-leve vmoptions file).
            // Therefore, this code sets an opposite option.
            changedOptions += prefix to "false"
          }
        }
        else if (forceDefaultFs && actualValue != "true") {
          changedOptions += prefix to "true"
        }
      }

      return changedOptions
    }
  }

  init {
    if (!SystemInfo.isWindows) {
      thisLogger().error("${javaClass.name} should be requested only on Windows")
    }
  }

  fun ensureInVmOptions(): Collection<Pair<String, String?>> {
    val isEnabled = WslIjentAvailabilityService.Companion.getInstance().useIjentForWslNioFileSystem()

    // In Dev Server, it's possible to customize VM options only through the Run Configuration.
    // Invoking the action "Customize VM Options" won't have any effect because the Dev Server resets the file on restart.
    val getEffectiveVmOptions = AppMode.isDevServer() || ApplicationManager.getApplication().isUnitTestMode

    val changedOptions = ensureInVmOptionsImpl(isEnabled, false) { prefix ->
      VMOptions.readOption(prefix, getEffectiveVmOptions)
    }

    for ((prefix, value) in changedOptions) {
      try {
        VMOptions.setOption(prefix, value)
      }
      catch (err: IOException) {
        if (!ApplicationManager.getApplication().isUnitTestMode) {
          throw err
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