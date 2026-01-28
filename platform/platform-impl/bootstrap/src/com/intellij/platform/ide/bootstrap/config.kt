// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.bootstrap

import com.intellij.accessibility.enableScreenReaderSupportIfNecessary
import com.intellij.idea.AppMode
import com.intellij.openapi.application.ConfigImportHelper
import com.intellij.openapi.application.CustomConfigMigrationOption
import com.intellij.openapi.application.InitialConfigImportState
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.util.registry.EarlyAccessRegistryManager
import com.intellij.platform.diagnostic.telemetry.impl.span
import com.intellij.ui.ExperimentalUI
import com.intellij.util.ui.RawSwingDispatcher
import kotlinx.coroutines.*
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CancellationException
import kotlin.io.path.name

internal suspend fun importConfigIfNeeded(
  scope: CoroutineScope,
  isHeadless: Boolean,
  configImportNeededDeferred: Deferred<Boolean>,
  lockSystemDirsJob: Job,
  logDeferred: Deferred<Logger>,
  args: List<String>,
  customTargetDirectoryToImportConfig: Path?,
  appStarterDeferred: Deferred<AppStarter>,
  euaDocumentDeferred: Deferred<EndUserAgreementStatus>,
  initLafJob: Job,
): Job? {
  if (!configImportNeededDeferred.await()) {
    val configDir = PathManager.getConfigDir()
    val configDirExists = Files.exists(configDir)
    val entries = NioFiles.list(configDir).joinToString(", ") { "\"${it.name}\"" }
    scope.launch {
      logDeferred.await().info("Will skip the config import to directory \"$configDir\" (exists = $configDirExists). Current entries: ${entries}.")
    }
    if (ClassicUiToIslandsMigration.isEnabledFeature) {
      // Possible minor update
      ClassicUiToIslandsMigration.enableNewUiWithIslands(logDeferred)
    }
    return null
  }

  if (isHeadless) {
    importConfigHeadless(lockSystemDirsJob, logDeferred)
    val log = logDeferred.await()
    if (shouldMigrateConfigOnNextRun(args)) {
      val configDir = PathManager.getOriginalConfigDir()
      if (!CustomConfigMigrationOption.doesCustomConfigMarkerExist(configDir)) {
        log.info("writing marker to $configDir to ensure that config will be imported next time")
        CustomConfigMigrationOption.MergeConfigs.writeConfigMarkerFile()
      }
    }
    log.info("config importing not performed in headless mode")
    return null
  }

  initLafJob.join()
  val log = logDeferred.await()
  val targetDirectoryToImportConfig = customTargetDirectoryToImportConfig ?: PathManager.getConfigDir()
  val entries = NioFiles.list(targetDirectoryToImportConfig).joinToString(", ") { "\"${it.name}\"" }
  log.info("Will import config to directory \"$targetDirectoryToImportConfig\" (exists = ${Files.exists(targetDirectoryToImportConfig)}). Current entries: ${entries}.")
  importConfig(args, targetDirectoryToImportConfig, log, appStarterDeferred.await(), euaDocumentDeferred)

  val isNewUser = InitialConfigImportState.isNewUser()

  if (ClassicUiToIslandsMigration.isEnabledFeature) {
    ClassicUiToIslandsMigration.enableNewUiWithIslands(logDeferred)
  } else {
    enableNewUi(logDeferred, isNewUser)
  }

  if (isNewUser && InitialConfigImportState.isStartupWizardEnabled()) {
    log.info("Will enter initial app wizard flow.")
    val result = CompletableDeferred<Boolean>()
    isInitialStart = result
    return result
  }

  return null
}

private fun shouldMigrateConfigOnNextRun(args: List<String>): Boolean {
  val command = args.firstOrNull()
  //currently, migration of config will be performed on the next run only for headless commands from remote dev mode only; later we can enable this behavior for all commands
  val headlessCommands = listOf("cwmHostStatus", "remoteDevStatus", "cwmHost", "invalidateCaches", "remoteDevShowHelp", "openUrlOnClient", "registerBackendLocationForGateway")
  return headlessCommands.contains(command)
}

private suspend fun importConfigHeadless(lockSystemDirsJob: Job, logDeferred: Deferred<Logger>) {
  // make sure we lock the dir before writing
  lockSystemDirsJob.join()
  enableNewUi(logDeferred, isBackgroundSwitch = !AppMode.isRemoteDevHost())
}

private suspend fun importConfig(
  args: List<String>,
  targetDirectoryToImportConfig: Path,
  log: Logger,
  appStarter: AppStarter,
  euaDocumentDeferred: Deferred<EndUserAgreementStatus>,
) {
  span("screen reader checking") {
    runCatching {
      enableScreenReaderSupportIfNecessary()
    }.getOrLogException(log)
  }

  span("config importing") {
    appStarter.beforeImportConfigs()

    val euaDocumentStatus = euaDocumentDeferred.await()
    val veryFirstStartOnThisComputer = euaDocumentStatus is EndUserAgreementStatus.Required
    val log = logger<ConfigImportHelper>()
    withContext(RawSwingDispatcher) {
      ConfigImportHelper.importConfigsTo(veryFirstStartOnThisComputer, targetDirectoryToImportConfig, args, log)
    }
    appStarter.importFinished(targetDirectoryToImportConfig)
    EarlyAccessRegistryManager.invalidate()
    IconLoader.clearCache()
  }
}

private suspend fun enableNewUi(logDeferred: Deferred<Logger>, isBackgroundSwitch: Boolean) {
  try {
    val shouldEnableNewUi = !EarlyAccessRegistryManager.getBoolean("ide.experimental.ui") && !EarlyAccessRegistryManager.getBoolean("moved.to.new.ui")
    if (shouldEnableNewUi) {
      EarlyAccessRegistryManager.setAndFlush(mapOf("ide.experimental.ui" to "true", "moved.to.new.ui" to "true"))
      if (!isBackgroundSwitch) {
        ExperimentalUI.forcedSwitchedUi = true
      }
    }
  }
  catch (e: CancellationException) {
    throw e
  }
  catch (e: Throwable) {
    logDeferred.await().error(e)
  }
}
