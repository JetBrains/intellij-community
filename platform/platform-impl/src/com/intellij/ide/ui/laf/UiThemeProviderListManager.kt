// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf

import com.intellij.ide.ui.UIThemeProvider
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.impl.EditorColorsManagerImpl
import com.intellij.ui.ExperimentalUI

// separate service to avoid using LafManager in the EditorColorsManagerImpl initialization
@Service(Service.Level.APP)
internal class UiThemeProviderListManager {
  companion object {
    @JvmStatic
    fun getInstance(): UiThemeProviderListManager = service()
  }

  @Volatile
  private var lafList = computeList()

  fun getLaFs(): List<UIThemeBasedLookAndFeelInfo> = lafList

  fun themeAdded(provider: UIThemeProvider): UIThemeBasedLookAndFeelInfo? {
    if (lafList.any { it.theme.id == provider.id }) {
      // provider is already registered
      return null
    }

    val theme = provider.createTheme() ?: return null
    (EditorColorsManager.getInstance() as EditorColorsManagerImpl).handleThemeAdded(theme)
    val newLaF = UIThemeBasedLookAndFeelInfo(theme)
    lafList = lafList + newLaF
    return newLaF
  }
}

private fun computeList(): List<UIThemeBasedLookAndFeelInfo> {
  val themes = mutableListOf<UIThemeBasedLookAndFeelInfo>()
  UIThemeProvider.EP_NAME.forEachExtensionSafe { provider ->
    if (shouldCreateTheme(provider)) {
      themes.add(UIThemeBasedLookAndFeelInfo(provider.createTheme() ?: return@forEachExtensionSafe))
    }
  }
  return themes
}

private fun shouldCreateTheme(provider: UIThemeProvider): Boolean {
  return provider.id != LafManagerImpl.DEFAULT_LIGHT_THEME_ID &&
         (ExperimentalUI.isNewUI() || (provider.id != "ExperimentalLight" && provider.id != "ExperimentalDark"))
}