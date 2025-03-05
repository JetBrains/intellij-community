// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.filters

import com.intellij.openapi.components.*
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@State(
  name = "StackTraceFoldingSettings",
  /** The same XML file is used by [com.intellij.execution.console.ConsoleFoldingSettings] */
  storages = [Storage("consoleFolding.xml")],
  category = SettingsCategory.CODE
)
class StackTraceFoldingSettings :
  SimplePersistentStateComponent<StackTraceFoldingSettings.State>(State()) {

  class State : BaseState() {
    var foldJavaStackTrace by property(true)
    var foldJavaStackTraceGreaterThan by property(8)
  }

  var foldJavaStackTrace: Boolean
    get() = state.foldJavaStackTrace
    set(value) { state.foldJavaStackTrace = value }

  var foldJavaStackTraceGreaterThan: Int
    get() = state.foldJavaStackTraceGreaterThan
    set(value) { state.foldJavaStackTraceGreaterThan = value }

  companion object {
    @JvmStatic
    fun getInstance(): StackTraceFoldingSettings = service()
  }
}
