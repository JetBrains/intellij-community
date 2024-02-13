// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lvcs.impl.settings

import com.intellij.openapi.components.*

@State(name = "Lvcs.Activity.App.Settings", storages = [Storage("lvcs.xml")], category = SettingsCategory.UI)
class ActivityViewApplicationSettings : SimplePersistentStateComponent<ActivityViewApplicationSettings.State>(State()) {

  class State : BaseState() {
    var isActivityToolWindowEnabled by property(true)
  }

  var isActivityToolWindowEnabled: Boolean
    get() = state.isActivityToolWindowEnabled
    set(value) {
      state.isActivityToolWindowEnabled = value
    }
}