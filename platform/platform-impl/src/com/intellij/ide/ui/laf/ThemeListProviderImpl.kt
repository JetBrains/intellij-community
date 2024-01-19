// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf

import com.intellij.ide.ui.TargetUIType
import com.intellij.ide.ui.ThemeListProvider
import com.intellij.ui.ExperimentalUI

private class ThemeListProviderImpl : ThemeListProvider {
  override fun getShownThemes(): List<List<UIThemeLookAndFeelInfo>> {
    val result = mutableListOf<List<UIThemeLookAndFeelInfo>>()
    val uiThemeProviderListManager = UiThemeProviderListManager.getInstance()

    val defaultAuthor = "JetBrains"
    val newUiThemes = mutableListOf<UIThemeLookAndFeelInfo>()
    val classicUiThemes = mutableListOf<UIThemeLookAndFeelInfo>()
    val customThemes = mutableListOf<UIThemeLookAndFeelInfo>()

    val highContrastThemeId = "JetBrainsHighContrastTheme"
    val highContrastThemeToAdd = uiThemeProviderListManager.findThemeById(highContrastThemeId)

    if (ExperimentalUI.isNewUI()) {
      uiThemeProviderListManager.getThemeListForTargetUI(TargetUIType.NEW).forEach { info ->
        if (info.author == defaultAuthor) newUiThemes.add(info)
        else customThemes.add(info)
      }
    }

    (uiThemeProviderListManager.getThemeListForTargetUI(TargetUIType.CLASSIC) +
     uiThemeProviderListManager.getThemeListForTargetUI(TargetUIType.UNSPECIFIED))
      .forEach { info ->
        if (info.id == highContrastThemeId
            || info.id == "IntelliJ"
            || (info.id == "JetBrainsLightTheme" && ExperimentalUI.isNewUI())) return@forEach

        if (info.author == defaultAuthor) classicUiThemes.add(info)
        else customThemes.add(info)
      }

    newUiThemes.sortedBy { it.name }
    classicUiThemes.sortedBy { it.name }
    customThemes.sortedBy { it.name }

    if (newUiThemes.isNotEmpty()) result.add(newUiThemes)
    if (classicUiThemes.isNotEmpty()) result.add(classicUiThemes)
    if (customThemes.isNotEmpty()) result.add(customThemes)

    if (highContrastThemeToAdd != null) {
      (result.firstOrNull() as? MutableList)?.add(highContrastThemeToAdd)
    }

    return result
  }
}