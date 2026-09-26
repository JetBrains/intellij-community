// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.marketplace.statistics.enums

import com.intellij.openapi.actionSystem.ActionPlaces
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
enum class PluginManagerOpenSourceEnum {
  TOOLBAR, NOTIFICATION, ACTION_SEARCH, SETTINGS, WELCOME_SCREEN, OTHER;

  companion object {
    @JvmStatic
    fun fromActionPlace(place: String?): PluginManagerOpenSourceEnum = when (place) {
      ActionPlaces.ACTION_SEARCH -> ACTION_SEARCH
      ActionPlaces.NOTIFICATION -> NOTIFICATION
      ActionPlaces.WELCOME_SCREEN -> WELCOME_SCREEN
      else -> TOOLBAR
    }
  }
}
