// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui

import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.annotations.ApiStatus
import javax.swing.UIManager

/**
 * Provides all available LaFs sorted and grouped for popups/combobox lists
 */
@ApiStatus.Internal
interface ThemesListProvider {

  companion object {
    @JvmStatic
    fun getInstance(): ThemesListProvider = ApplicationManager.getApplication().getService(ThemesListProvider::class.java)
  }

  /**
   * Provides all available themes.
   * Themes are divided to groups, groups should be split by separators in all UIs
   */
  fun getShownThemes(): List<List<UIManager.LookAndFeelInfo>>

}