// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.ide.ui.laf

import com.intellij.ide.ui.UIThemeProvider
import com.intellij.ide.ui.laf.UiThemeProviderListManager.Companion.sortThemes
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.impl.EditorColorsManagerImpl
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.ui.ExperimentalUI
import javax.swing.UIManager.LookAndFeelInfo

// separate service to avoid using LafManager in the EditorColorsManagerImpl initialization
@Service(Service.Level.APP)
internal class UiThemeProviderListManager {
  companion object {
    @JvmStatic
    fun getInstance(): UiThemeProviderListManager = service()

    const val DEFAULT_LIGHT_THEME_ID = "JetBrainsLightTheme"

    var lafNameOrder: Map<String, Int> = java.util.Map.of(
      "IntelliJ Light", 0,
      "macOS Light", 1,
      "Windows 10 Light", 1,
      "Darcula", 2,
      "High contrast", 3
    )

    fun sortThemes(list: MutableList<out LookAndFeelInfo>) {
      list.sortWith { t1: LookAndFeelInfo, t2: LookAndFeelInfo ->
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
  }

  @Volatile
  private var lafList = computeList()

  fun getLaFs(): List<UIThemeBasedLookAndFeelInfo> = lafList

  fun findJetBrainsLightTheme(): UIThemeBasedLookAndFeelInfo? {
    return lafList.find { it.theme.id == DEFAULT_LIGHT_THEME_ID }
  }

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
  val point = UIThemeProvider.EP_NAME.point as ExtensionPointImpl<UIThemeProvider>
  val themes = ArrayList<UIThemeBasedLookAndFeelInfo>(point.size())
  val isNewUi = ExperimentalUI.isNewUI()
  for (adapter in point.sortedAdapters) {
    if (isNewUi || shouldCreateThemeForOldUi(adapter.orderId ?: "")) {
      val provider = adapter.createInstance<UIThemeProvider>(ApplicationManager.getApplication()) ?: continue
      themes.add(UIThemeBasedLookAndFeelInfo(provider.createTheme() ?: continue))
    }
  }
  sortThemes(themes)
  return themes
}

private fun shouldCreateThemeForOldUi(id: String): Boolean {
  return id != "ExperimentalLight" && id != "ExperimentalDark"
}