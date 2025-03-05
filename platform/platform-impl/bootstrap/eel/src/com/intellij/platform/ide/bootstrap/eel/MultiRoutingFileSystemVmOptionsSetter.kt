// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.bootstrap.eel

import com.intellij.diagnostic.VMOptions
import com.intellij.execution.wsl.WslIjentAvailabilityService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.core.nio.fs.MultiRoutingFileSystemProvider
import com.intellij.platform.ijent.community.buildConstants.IJENT_BOOT_CLASSPATH_MODULE
import com.intellij.platform.ijent.community.buildConstants.IJENT_WSL_FILE_SYSTEM_REGISTRY_KEY
import com.intellij.platform.ijent.community.buildConstants.isIjentWslFsEnabledByDefaultForProduct
import org.jetbrains.annotations.VisibleForTesting
import java.io.IOException
import java.lang.management.ManagementFactory

object MultiRoutingFileSystemVmOptionsSetter {
  @VisibleForTesting
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

    val serializedProperties: List<String> = ManagementFactory.getRuntimeMXBean().inputArguments
    val changedOptions = ensureInVmOptionsImpl(isEnabled, false) { prefix ->
      serializedProperties
        .find { systemProperty -> systemProperty.startsWith(prefix) }
        ?.removePrefix(prefix)
    }

    for ((prefix, value) in changedOptions) {
      try {
        VMOptions.setOption(prefix, value)
      }
      catch (err: IOException) {
        if (!ApplicationManager.getApplication().isUnitTestMode) {
          logger<MultiRoutingFileSystemVmOptionsSetter>().error("Failed to set VM Option for IJent file system", err)
        }
      }
    }

    return changedOptions
  }
}
