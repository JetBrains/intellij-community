// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.command.configuration

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.PlatformUtils
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly


/**
 * Service responsible for managing and persisting command completion settings at the application level.
 */
@Service(Service.Level.APP)
@ApiStatus.Internal
@State(name = "CommandCompletion", category = SettingsCategory.UI, storages = [Storage("CommandCompletion.xml", roamingType = RoamingType.DISABLED)])
internal class ApplicationCommandCompletionService : PersistentStateComponent<AppCommandCompletionSettings> {

  companion object {
     internal fun getInstance(): ApplicationCommandCompletionService = ApplicationManager.getApplication().getService(ApplicationCommandCompletionService::class.java)
  }

  private var myState = AppCommandCompletionSettings()

  override fun getState(): AppCommandCompletionSettings = myState

  override fun loadState(state: AppCommandCompletionSettings) {
    myState = state
  }

  fun commandCompletionEnabled(): Boolean {
    return myState.isEnabled()
  }

  fun useGroupEnabled(): Boolean {
    return myState.useGroup
  }

  fun readOnlyEnabled(): Boolean {
    return myState.myReadOnlyEnabled
  }
}

@ApiStatus.Internal
@Service(Service.Level.APP)
class CommandCompletionSettingsService {
  companion object {
    @JvmStatic
    fun getInstance(): CommandCompletionSettingsService = ApplicationManager.getApplication().getService(CommandCompletionSettingsService::class.java)
  }

  fun commandCompletionEnabled(): Boolean {
    return ApplicationCommandCompletionService.getInstance().commandCompletionEnabled()
  }

  fun groupEnabled(): Boolean {
    return ApplicationCommandCompletionService.getInstance().useGroupEnabled()
  }

  fun readOnlyEnabled(): Boolean {
    return ApplicationCommandCompletionService.getInstance().readOnlyEnabled()
  }

  @TestOnly
  fun groupEnabled(enabled: Boolean) {
    val service = ApplicationCommandCompletionService.getInstance()
    service.state.useGroup = enabled
  }

  @TestOnly
  fun readOnlyEnabled(enabled: Boolean) {
    val service = ApplicationCommandCompletionService.getInstance()
    service.state.myReadOnlyEnabled = enabled
  }
}

@ApiStatus.Internal
internal class AppCommandCompletionSettings(
  var showCounts: Int = 0,
  var myEnabled: CommandCompletionEnabled = CommandCompletionEnabled.FROM_REGISTRY,
  var myReadOnlyEnabled: Boolean = false,
  var useGroup: Boolean = true,
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
    if (ApplicationManager.getApplication().isUnitTestMode() &&
        Registry.`is`("ide.completion.command.force.enabled")) return true
    if (!PlatformUtils.isIntelliJ()) return false
    return Registry.`is`("ide.completion.command.force.enabled") ||
           (!ApplicationManager.getApplication().isUnitTestMode() &&
            Registry.`is`("ide.completion.command.enabled"))
  }
}

internal enum class CommandCompletionEnabled {
  ENABLED, DISABLED, FROM_REGISTRY;
}