// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands.core

import com.intellij.openapi.components.*
import org.jetbrains.annotations.ApiStatus


@Service(Service.Level.APP)
@ApiStatus.Experimental
@ApiStatus.Internal
@State(name = "CommandCompletion", category = SettingsCategory.UI, storages = [Storage("CommandCompletion.xml", roamingType = RoamingType.DISABLED)])
class ApplicationCommandCompletionService : PersistentStateComponent<AppCommandCompletionSettings> {

  private var myState = AppCommandCompletionSettings()

  override fun getState(): AppCommandCompletionSettings = myState

  override fun loadState(state: AppCommandCompletionSettings) {
    myState = state
  }
}

class AppCommandCompletionSettings(
  var showCounts: Int = 0,
)