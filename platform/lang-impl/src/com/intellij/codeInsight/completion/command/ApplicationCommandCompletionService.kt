// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.command

import com.intellij.openapi.components.*
import org.jetbrains.annotations.ApiStatus


/**
 * Service responsible for managing and persisting command completion settings at the application level.
 *
 */
@Service(Service.Level.APP)
@ApiStatus.Internal
@State(name = "CommandCompletion", category = SettingsCategory.UI, storages = [Storage("CommandCompletion.xml", roamingType = RoamingType.DISABLED)])
internal class ApplicationCommandCompletionService : PersistentStateComponent<AppCommandCompletionSettings> {

  private var myState = AppCommandCompletionSettings()

  override fun getState(): AppCommandCompletionSettings = myState

  override fun loadState(state: AppCommandCompletionSettings) {
    myState = state
  }
}

@ApiStatus.Internal
internal class AppCommandCompletionSettings(
  var showCounts: Int = 0,
)