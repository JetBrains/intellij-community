// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui

import com.intellij.ide.ui.laf.UIThemeLookAndFeelInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import org.jetbrains.annotations.ApiStatus

/**
 * Provides all available LaFs sorted and grouped for popups/combobox lists
 */
@ApiStatus.Internal
interface ThemeListProvider {
  companion object {
    fun getInstance(): ThemeListProvider = ApplicationManager.getApplication().service<ThemeListProvider>()
  }

  /**
   * Provides all available themes.
   * Themes are divided to groups, groups should be split by separators in all UIs
   */
  fun getShownThemes(): List<List<UIThemeLookAndFeelInfo>>
}