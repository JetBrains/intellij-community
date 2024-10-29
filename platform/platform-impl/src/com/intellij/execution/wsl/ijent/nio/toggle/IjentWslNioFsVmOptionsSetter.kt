// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl.ijent.nio.toggle

import com.intellij.CommonBundle
import com.intellij.diagnostic.VMOptions
import com.intellij.execution.wsl.WslIjentAvailabilityService
import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.idea.AppMode
import com.intellij.openapi.application.*
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.IdeFrame
import com.intellij.platform.core.nio.fs.CoreBootstrapSecurityManager
import com.intellij.platform.core.nio.fs.MultiRoutingFileSystemProvider
import com.intellij.platform.ijent.community.buildConstants.IJENT_BOOT_CLASSPATH_MODULE
import com.intellij.platform.ijent.community.buildConstants.IJENT_WSL_FILE_SYSTEM_REGISTRY_KEY
import com.intellij.platform.ijent.community.buildConstants.isIjentWslFsEnabledByDefaultForProduct
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.annotations.VisibleForTesting
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

@VisibleForTesting
object IjentWslNioFsVmOptionsSetter {
  fun ensureInVmOptionsImpl(
    isEnabled: Boolean,
    forceProductionOptions: Boolean,
    isEnabledByDefault: Boolean = isIjentWslFsEnabledByDefaultForProduct(ApplicationNamesInfo.getInstance().scriptName),
    getOptionByPrefix: (String) -> String?,
  ): Collection<Pair<String, String?>> {
    val changedOptions = mutableListOf<Pair<String, String?>>()

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

    run {
      val prefix = "-D$IJENT_WSL_FILE_SYSTEM_REGISTRY_KEY="
      val valueToSet = when (val actualValue = getOptionByPrefix(prefix)?.toBooleanStrictOrNull()) {
        null ->
          if (isEnabled != isEnabledByDefault && changedOptions.isNotEmpty()) isEnabled
          else null
        true, false ->
          if (isEnabled != actualValue) isEnabled
          else null
      }
      if (valueToSet != null) {
        changedOptions += prefix to valueToSet.toString()
      }
    }

    return changedOptions
  }

  fun ensureInVmOptions(): Collection<Pair<String, String?>> {
    val isEnabled = WslIjentAvailabilityService.getInstance().useIjentForWslNioFileSystem()

    // In Dev Server, it's possible to customize VM options only through the Run Configuration.
    // Invoking the action "Customize VM Options" won't have any effect because the Dev Server resets the file on restart.
    val getEffectiveVmOptions =
      PluginManagerCore.isRunningFromSources() ||
      AppMode.isDevServer() ||
      ApplicationManager.getApplication().isUnitTestMode ||
      !VMOptions.canWriteOptions()  // It happens when the IDE is launched from `.\gradlew runIde` with intellij-platform-gradle-plugin.

    val changedOptions = ensureInVmOptionsImpl(isEnabled, false) { prefix ->
      VMOptions.readOption(prefix, getEffectiveVmOptions)
    }

    for ((prefix, value) in changedOptions) {
      try {
        VMOptions.setOption(prefix, value)
      }
      catch (err: IOException) {
        if (!ApplicationManager.getApplication().isUnitTestMode) {
          thisLogger().error("Failed to set VM Option for IJent file system", err)
        }
      }
    }

    return changedOptions
  }

  internal class ApplicationListener : ApplicationActivationListener {
    @Service
    private class ServiceScope(coroutineScope: CoroutineScope) : CoroutineScope by coroutineScope {
      val dialogMessageHasBeenShown = AtomicBoolean(false)
    }

    override fun applicationActivated(ideFrame: IdeFrame) {
      if (!WslIjentAvailabilityService.getInstance().useIjentForWslNioFileSystem()) return

      val serviceScope = service<ServiceScope>()
      if (serviceScope.dialogMessageHasBeenShown.getAndSet(true)) return

      serviceScope.launch {
        val changedOptions = ensureInVmOptions()
        when {
          changedOptions.isEmpty() -> {
            IjentWslNioFsToggler.instanceAsync().enableForAllWslDistributions()
          }

          PluginManagerCore.isRunningFromSources() || AppMode.isDevServer() -> {
            logger<IjentWslNioFsVmOptionsSetter>().warn(
              changedOptions.joinToString(
                prefix = "This message is seen only in Dev Mode/Run from sources.\n" +
                         "The value of the registry flag `$IJENT_WSL_FILE_SYSTEM_REGISTRY_KEY` for IJent FS " +
                         "doesn't match the VM options.\n" +
                         "Add the following VM options to the Run Configuration:\n",
                separator = "\n",
              ) { (k, v) ->
                "  $k${v.orEmpty()}"
              }
            )
          }

          else -> launch(Dispatchers.EDT + ModalityState.nonModal().asContextElement()) {
            val doThat = Messages.OK == Messages.showOkCancelDialog(
              null,
              IdeBundle.message("ijent.wsl.fs.dialog.message"),
              IdeBundle.message("ijent.wsl.fs.dialog.title"),
              IdeBundle.message(if (ApplicationManager.getApplication().isRestartCapable) "ide.restart.action" else "ide.shutdown.action"),
              CommonBundle.getCancelButtonText(),
              AllIcons.General.Warning,
            )
            if (doThat) {
              ApplicationManagerEx.getApplicationEx().restart(true)
            }
          }
        }
      }
    }
  }
}
