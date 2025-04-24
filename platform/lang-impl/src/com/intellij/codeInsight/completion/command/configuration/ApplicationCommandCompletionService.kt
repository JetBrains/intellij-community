// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.command.configuration

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.PlatformUtils
import org.jetbrains.annotations.ApiStatus


/**
 * Service responsible for managing and persisting command completion settings at the application level.
 */
@Service(Service.Level.APP)
@ApiStatus.Internal
@State(name = "CommandCompletion", category = SettingsCategory.UI, storages = [Storage("CommandCompletion.xml", roamingType = RoamingType.DISABLED)])
internal class ApplicationCommandCompletionService : PersistentStateComponent<AppCommandCompletionSettings> {

  companion object {
    @JvmStatic
    fun getInstance(): ApplicationCommandCompletionService = ApplicationManager.getApplication().getService(ApplicationCommandCompletionService::class.java)
  }

  private var myState = AppCommandCompletionSettings()

  override fun getState(): AppCommandCompletionSettings = myState

  override fun loadState(state: AppCommandCompletionSettings) {
    myState = state
  }

  fun commandCompletionEnabled(): Boolean {
    return myState.isEnabled()
  }
}

@ApiStatus.Internal
internal class AppCommandCompletionSettings(
  var showCounts: Int = 0,
  var myEnabled: CommandCompletionEnabled = CommandCompletionEnabled.FROM_REGISTRY,
) {

  fun isEnabled(): Boolean {
    if (myEnabled == CommandCompletionEnabled.DISABLED) return false
    if (myEnabled == CommandCompletionEnabled.ENABLED) return true
    val fromRegistry = calculateFromRegistry()
    return fromRegistry
  }

  fun setEnabled(enabled: Boolean) {
    if (myEnabled != CommandCompletionEnabled.FROM_REGISTRY) {
      myEnabled = if (enabled) CommandCompletionEnabled.ENABLED else CommandCompletionEnabled.DISABLED
      return
    }
    val fromRegistry = calculateFromRegistry()
    if (fromRegistry == enabled) {
      return
    }
    myEnabled = if (enabled) CommandCompletionEnabled.ENABLED else CommandCompletionEnabled.DISABLED
  }

  private fun calculateFromRegistry(): Boolean {
    if (!PlatformUtils.isIntelliJ()) return false
    return Registry.`is`("ide.completion.command.force.enabled") ||
           (ApplicationManager.getApplication().isInternal() &&
            !ApplicationManager.getApplication().isUnitTestMode() &&
            Registry.`is`("ide.completion.command.enabled"))
  }
}

internal enum class CommandCompletionEnabled {
  ENABLED, DISABLED, FROM_REGISTRY;
}