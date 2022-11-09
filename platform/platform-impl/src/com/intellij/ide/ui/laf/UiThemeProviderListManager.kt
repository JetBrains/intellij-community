// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.ide.ui.laf

import com.intellij.ide.ui.UIThemeProvider
import com.intellij.ide.ui.laf.UiThemeProviderListManager.Companion.sortThemes
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.impl.EditorColorsManagerImpl
import com.intellij.ui.ExperimentalUI
import com.intellij.util.PlatformUtils
import javax.swing.UIManager.LookAndFeelInfo

// separate service to avoid using LafManager in the EditorColorsManagerImpl initialization
@Service(Service.Level.APP)
internal class UiThemeProviderListManager {
  companion object {
    @JvmStatic
    fun getInstance(): UiThemeProviderListManager = service()

    private const val DEFAULT_LIGHT_THEME_ID = "JetBrainsLightTheme"

    var lafNameOrder: Map<String, Int> = if (ExperimentalUI.isNewUI()) {
      java.util.Map.of(
        "Light", 0,
        "Dark", 1,
        "High contrast", 2
      )
    }
    else if (PlatformUtils.isRider()) {
      java.util.Map.of(
        "Rider Dark", 0,
        "Rider Light", 1,
        "IntelliJ Light", 2,
        "macOS Light", 3,
        "Windows 10 Light", 3,
        "Darcula", 4,
        "High contrast", 5
      )
    }
    else {
      java.util.Map.of(
        "IntelliJ Light", 0,
        "macOS Light", 1,
        "Windows 10 Light", 1,
        "Darcula", 2,
        "High contrast", 3
      )
    }

    val excludedThemes: List<String>
      get() = if (!ExperimentalUI.isNewUI()) listOf("Light", "Dark", "New Dark") else emptyList()

    fun sortThemes(list: MutableList<out LookAndFeelInfo>) {
      list.sortWith { t1, t2 ->
        val n1 = t1.name
        val n2 = t2.name
        if (n1 == n2) {
          return@sortWith 0
        }

        val o1 = lafNameOrder.get(n1)
        val o2 = lafNameOrder.get(n2)
        when {
          o1 != null && o2 != null -> o1 - o2
          o1 != null -> -1
          o2 != null -> 1
          else -> n1.compareTo(n2, ignoreCase = true)
        }
      }
    }

    private fun editorColorsManager() = EditorColorsManager.getInstance() as EditorColorsManagerImpl
  }

  @Volatile
  private var lafList = computeList()

  fun getLaFs(): List<UIThemeBasedLookAndFeelInfo> = lafList

  fun findJetBrainsLightTheme(): UIThemeBasedLookAndFeelInfo? = findLaFById(DEFAULT_LIGHT_THEME_ID)

  fun themeProviderAdded(provider: UIThemeProvider): UIThemeBasedLookAndFeelInfo? {
    if (findLaFByProviderId(provider) != null) {
      // provider is already registered
      return null
    }

    val theme = provider.createTheme() ?: return null
    editorColorsManager().handleThemeAdded(theme)
    val newLaF = UIThemeBasedLookAndFeelInfo(theme)
    lafList = lafList + newLaF
    return newLaF
  }

  fun themeProviderRemoved(provider: UIThemeProvider): UIThemeBasedLookAndFeelInfo? {
    val oldLaF = findLaFByProviderId(provider) ?: return null

    lafList = lafList - oldLaF
    editorColorsManager().handleThemeRemoved(oldLaF.theme)
    return oldLaF
  }

  private fun findLaFById(id: String) = lafList.find { it.theme.id == id }

  private fun findLaFByProviderId(provider: UIThemeProvider) = findLaFById(provider.id)
}

private fun computeList(): List<UIThemeBasedLookAndFeelInfo> {
  val themes = ArrayList<UIThemeBasedLookAndFeelInfo>(UIThemeProvider.EP_NAME.point.size())
  UIThemeProvider.EP_NAME.processExtensions { provider, _ ->
    themes.add(UIThemeBasedLookAndFeelInfo(provider.createTheme() ?: return@processExtensions))
  }
  sortThemes(themes)
  return themes
}