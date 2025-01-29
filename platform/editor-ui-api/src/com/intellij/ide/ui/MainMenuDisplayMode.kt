// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui

import com.intellij.core.CoreBundle
import com.intellij.openapi.util.NlsActions

/**
 * Represents the configuration options for the main menu displaying.
 * This class is applicable exclusively for platforms running Linux and Windows operating systems.
**/
enum class MainMenuDisplayMode(@NlsActions.ActionText val description: String) {
  UNDER_HAMBURGER_BUTTON(CoreBundle.message("main.menu.under.hamburger.description")),
  MERGED_WITH_MAIN_TOOLBAR(CoreBundle.message("main.menu.merged.description")),
  SEPARATE_TOOLBAR(CoreBundle.message ("main.menu.separate.toolbar.description"));

  companion object {
    fun valueOf(name: String?): MainMenuDisplayMode = MainMenuDisplayMode.entries.find { it.name.equals(name, true)} ?: UNDER_HAMBURGER_BUTTON
  }

  override fun toString(): String = description
}