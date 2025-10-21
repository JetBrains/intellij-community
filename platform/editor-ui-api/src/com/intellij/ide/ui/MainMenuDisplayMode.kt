// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui

import com.intellij.core.CoreBundle
import com.intellij.openapi.util.NlsActions
import org.jetbrains.annotations.ApiStatus

/**
 * Represents the configuration options for the main menu displaying.
 * This class is applicable exclusively for platforms running Linux and Windows operating systems.
 **/
@ApiStatus.Internal
enum class MainMenuDisplayMode(@NlsActions.ActionText private val lazyDescription: () -> String) {
  UNDER_HAMBURGER_BUTTON(CoreBundle.messagePointer("main.menu.under.hamburger.description")),
  MERGED_WITH_MAIN_TOOLBAR(CoreBundle.messagePointer("main.menu.merged.description")),
  SEPARATE_TOOLBAR(CoreBundle.messagePointer("main.menu.separate.toolbar.description"));

  companion object {
    fun valueOf(name: String?): MainMenuDisplayMode {
      return MainMenuDisplayMode.entries.find { it.name.equals(name, true) } ?: UNDER_HAMBURGER_BUTTON
    }
  }

  val description: String
    get() = lazyDescription()

  override fun toString(): String = lazyDescription()
}