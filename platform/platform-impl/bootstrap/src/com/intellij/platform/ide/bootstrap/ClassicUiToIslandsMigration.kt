// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.bootstrap

import com.intellij.ide.plugins.DisabledPluginsState
import com.intellij.ide.plugins.PluginManager
import com.intellij.openapi.application.InitialConfigImportState
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.registry.EarlyAccessRegistryManager
import com.intellij.ui.ExperimentalUI
import kotlinx.coroutines.Deferred
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Todo can be removed after some time (e.g. in 2026.3). Additionally remove:
 * * com.intellij.platform.ide.bootstrap.ConfigKt.enableNewUi
 * * [ExperimentalUI.forcedSwitchedUi]
 * * [ExperimentalUI.showNewUiOnboarding]
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

  private const val MOVED_TO_NEW_UI_WITH_ISLANDS = "moved.to.new.ui.with.islands"
  private val movedToNewUiWithIslands: Boolean?
    get() = EarlyAccessRegistryManager.getString(MOVED_TO_NEW_UI_WITH_ISLANDS)?.toBoolean()
  private val classicUiPluginId: PluginId
    get() = PluginId.getId("com.intellij.classic.ui")


  suspend fun enableNewUiWithIslands(logDeferred: Deferred<Logger>) {
    try {
      val movedToNewUiWithIslands = movedToNewUiWithIslands
      if (movedToNewUiWithIslands != null) {
        // Processed once
        return
      }

      if (InitialConfigImportState.isNewUser() || EarlyAccessRegistryManager.getBoolean("ide.experimental.ui")) {
        EarlyAccessRegistryManager.setAndFlush(mapOf(MOVED_TO_NEW_UI_WITH_ISLANDS to "false"))
      }
      else {
        EarlyAccessRegistryManager.setAndFlush(mapOf("ide.experimental.ui" to "true", MOVED_TO_NEW_UI_WITH_ISLANDS to "true"))

        // We don't know yet if the classic UI is installed, therefore, always disable the plugin
        addClassicUiToDisabledPlugins()
        ExperimentalUI.showNewUiOnboarding = true
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
    cleanUpClassicUIFromDisabledPlugins.compareAndSet(false, true)
  }
}
