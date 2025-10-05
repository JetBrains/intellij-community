// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf

import com.intellij.ide.IdeBundle
import com.intellij.ide.ui.TargetUIType
import com.intellij.ide.ui.ThemeListProvider
import com.intellij.openapi.editor.colors.Groups
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.ExperimentalUI

private class ThemeListProviderImpl : ThemeListProvider {
  override fun getShownThemes(): Groups<UIThemeLookAndFeelInfo> {
    val uiThemeProviderListManager = UiThemeProviderListManager.getInstance()

    val newUiThemes = mutableListOf<UIThemeLookAndFeelInfo>()
    val classicUiThemes = mutableListOf<UIThemeLookAndFeelInfo>()
    val nextUiThemes = mutableListOf<UIThemeLookAndFeelInfo>()
    val islandUiThemes = mutableListOf<UIThemeLookAndFeelInfo>()
    val islandsUiThemes = mutableListOf<UIThemeLookAndFeelInfo>()
    val customThemes = mutableListOf<UIThemeLookAndFeelInfo>()

    val intellijLightThemeId = "JetBrainsLightTheme"
    val highContrastThemeId = "JetBrainsHighContrastTheme"
    val highContrastThemeToAdd = uiThemeProviderListManager.findThemeById(highContrastThemeId)

    if (ExperimentalUI.isNewUI()) {
      uiThemeProviderListManager.getThemeListForTargetUI(TargetUIType.NEW).forEach { info ->
        if (!info.isThemeFromPlugin) newUiThemes.add(info)
        else customThemes.add(info)
      }
    }

    (uiThemeProviderListManager.getThemeListForTargetUI(TargetUIType.CLASSIC) +
     uiThemeProviderListManager.getThemeListForTargetUI(TargetUIType.UNSPECIFIED))
      .forEach { info ->
        if (info.id == highContrastThemeId
            || info.id == "IntelliJ"
            || (info.id == intellijLightThemeId && ExperimentalUI.isNewUI())) return@forEach

        if (!info.isThemeFromPlugin) classicUiThemes.add(info)
        else customThemes.add(info)
      }

    if (Registry.`is`("llm.riderNext.enabled", false)) {
      uiThemeProviderListManager.getThemeListForTargetUI(TargetUIType.NEXT).forEach { info ->
        if (!info.isThemeFromPlugin) nextUiThemes.add(info)
        else customThemes.add(info)
      }
    }
    else if (ExperimentalUI.isNewUI()) {
      uiThemeProviderListManager.getThemeListForTargetUI(TargetUIType.ISLANDS).forEach { info ->
        if (!info.isThemeFromPlugin) islandsUiThemes.add(info)
        else customThemes.add(info)
      }
    }

    newUiThemes.sortBy { it.name }
    classicUiThemes.sortBy { it.name }
    nextUiThemes.sortBy { it.name }
    islandUiThemes.sortBy { it.name }
    islandsUiThemes.sortBy { it.name }
    customThemes.sortBy { it.name }

    if (highContrastThemeToAdd != null) {
      if (newUiThemes.isEmpty()) {
        val intellijLightIndex = classicUiThemes.indexOfFirst { it.id == intellijLightThemeId }
        if (intellijLightIndex >= 0) {
          classicUiThemes.add(intellijLightIndex, highContrastThemeToAdd)
        }
        else classicUiThemes.add(highContrastThemeToAdd)
      }
      else newUiThemes.add(highContrastThemeToAdd)
    }

    val groupInfos = mutableListOf<Groups.GroupInfo<UIThemeLookAndFeelInfo>>()

    if (newUiThemes.isNotEmpty()) groupInfos.add(Groups.GroupInfo(newUiThemes))
    if (classicUiThemes.isNotEmpty()) groupInfos.add(Groups.GroupInfo(classicUiThemes))
    if (nextUiThemes.isNotEmpty()) groupInfos.add(Groups.GroupInfo(nextUiThemes))
    if (islandUiThemes.isNotEmpty()) groupInfos.add(Groups.GroupInfo(islandUiThemes))
    if (islandsUiThemes.isNotEmpty()) groupInfos.add(Groups.GroupInfo(islandsUiThemes))
    if (customThemes.isNotEmpty()) groupInfos.add(Groups.GroupInfo(customThemes, IdeBundle.message("combobox.list.custom.section.title")))

    return Groups(groupInfos)
  }
}