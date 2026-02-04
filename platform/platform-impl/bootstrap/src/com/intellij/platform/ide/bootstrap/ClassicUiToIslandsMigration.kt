// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.bootstrap

import com.intellij.ide.plugins.DisabledPluginsState
import com.intellij.ide.plugins.PluginManager
import com.intellij.openapi.application.InitialConfigImportState
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.buildNsUnawareJdom
import com.intellij.openapi.util.registry.EarlyAccessRegistryManager
import com.intellij.ui.ExperimentalUI
import com.intellij.util.PlatformUtils
import kotlinx.coroutines.Deferred
import java.nio.file.NoSuchFileException
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

private val LOG = logger<ClassicUiToIslandsMigration>()

/**
 * Todo can be removed after some time (e.g. in 2026.3). Additionally remove:
 * * com.intellij.platform.ide.bootstrap.ConfigKt.enableNewUi
 * * [ExperimentalUI.forcedSwitchedUi]
 * * [ExperimentalUI.SHOW_NEW_UI_ONBOARDING_ON_START]
 * * [ExperimentalUI.switchedFromClassicToIslandsInSession]
 * * [ExperimentalUI.switchedFromClassicToIslandsLafMigration]
 * * [ExperimentalUI.cleanUpClassicUIFromDisabled]
 */
internal object ClassicUiToIslandsMigration {

  val isEnabledFeature: Boolean by lazy {
    System.getProperty("disable.classic.ui.on.start.feature", "false").toBoolean()
  }

  /**
   * Remove the classic UI from the disabled plugins file if there is no classic UI plugin installed
   */
  private val cleanUpClassicUIFromDisabledPlugins = AtomicBoolean(false)

  private val classicUiPluginId: PluginId
    get() = PluginId.getId("com.intellij.classic.ui")


  suspend fun enableNewUiWithIslands(logDeferred: Deferred<Logger>) {
    try {
      val switchedFromClassicToIslands = ExperimentalUI.switchedFromClassicToIslands
      if (switchedFromClassicToIslands != null) {
        // Processed once
        return
      }

      val isNewUser = InitialConfigImportState.isNewUser()
      val experimentalUi = EarlyAccessRegistryManager.getBoolean("ide.experimental.ui")
      val allowedUserLafToMigrate = isAllowedUserLafToMigrate()

      LOG.info("Detect if switching to Islands needed: isNewUser=$isNewUser, experimentalUi=$experimentalUi, allowedUserLafToMigrate=$allowedUserLafToMigrate")

      if (isNewUser || experimentalUi || !allowedUserLafToMigrate) {
        EarlyAccessRegistryManager.setAndFlush(mapOf(ExperimentalUI.SWITCHED_FROM_CLASSIC_TO_ISLANDS to "false"))
      }
      else {
        LOG.info("Switching is needed and has been initiated")

        ExperimentalUI.switchedFromClassicToIslandsInSession = true
        ExperimentalUI.switchedFromClassicToIslandsLafMigration = true
        EarlyAccessRegistryManager.setAndFlush(mapOf("ide.experimental.ui" to "true", ExperimentalUI.SWITCHED_FROM_CLASSIC_TO_ISLANDS to "true"))

        // We don't know yet if the classic UI is installed, therefore, always disable the plugin
        addClassicUiToDisabledPlugins()
      }
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Throwable) {
      logDeferred.await().error(e)
    }
  }

  private fun cleanUpClassicUIFromDisabledPlugins() {
    if (!cleanUpClassicUIFromDisabledPlugins.compareAndSet(true, false)) {
      return
    }

    val classicUI = classicUiPluginId
    if (!DisabledPluginsState.getDisabledIds().contains(classicUI)) {
      return
    }
    if (PluginManager.isPluginInstalled(classicUI)) {
      // Keep disabled when classic UI is installed
      return
    }

    DisabledPluginsState.setEnabledState(setOf(classicUI), true)

    LOG.info("Classic UI removed from disabled plugins")
  }

  private fun addClassicUiToDisabledPlugins() {
    val classicUI = classicUiPluginId
    if (DisabledPluginsState.getDisabledIds().contains(classicUI)) {
      return
    }

    ExperimentalUI.cleanUpClassicUIFromDisabled = Runnable {
      cleanUpClassicUIFromDisabledPlugins()
    }

    DisabledPluginsState.setEnabledState(setOf(classicUI), false)

    LOG.info("Classic UI added to disabled plugins")

    cleanUpClassicUIFromDisabledPlugins.compareAndSet(false, true)
  }

  private fun isAllowedUserLafToMigrate(): Boolean {
    val defaultDarkLaf = if (PlatformUtils.isRider()) "RiderDark" else "Darcula"
    val defaultLightLaf = if (PlatformUtils.isRider()) "RiderLight" else "JetBrainsLightTheme"

    return when (getUserLaf(defaultDarkLaf)) {
      defaultDarkLaf, defaultLightLaf -> true
      else -> false
    }
  }

  private fun getUserLaf(defaultDarkLaf: String): String? {
    try {
      val lafPath = PathManager.getOptionsDir().resolve("laf.xml")
      val element = buildNsUnawareJdom(lafPath)

      return element.getChild("component")
               ?.getChild("laf")
               ?.getAttributeValue("themeId") ?: defaultDarkLaf
    }
    catch (_: NoSuchFileException) {
      return defaultDarkLaf
    }
    catch (e: Throwable) {
      LOG.info("Cannot parse laf.xml", e)

      return null
    }
  }
}
