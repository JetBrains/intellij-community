// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment")

package com.intellij.ide.ui

import com.intellij.openapi.util.IconPathPatcher
import com.intellij.ui.ExperimentalUI.Companion.isNewUI
import com.intellij.util.SVGLoader.SvgElementColorPatcherProvider

internal class UIThemeBean {
  companion object {
    /**
     * Ensure that the old themes are not missing some vital keys.
     *
     * We are patching them here instead of using [com.intellij.ui.JBColor.namedColor] fallback
     * to make sure [javax.swing.UIManager.getColor] works properly.
     */
    fun putDefaultsIfAbsent(theme: UIThemeBean) {
      var ui = theme.ui
      if (ui == null) {
        ui = LinkedHashMap()
        theme.ui = ui
      }
      if (isNewUI()) {
        ui.putIfAbsent("EditorTabs.underlineArc", "4")

        // require theme to specify ToolWindow stripe button colors explicitly, without "*"
        ui.putIfAbsent("ToolWindow.Button.selectedBackground", "#3573F0")
        ui.putIfAbsent("ToolWindow.Button.selectedForeground", "#FFFFFF")
      }
    }

    fun importFromParentTheme(theme: UIThemeBean, parentTheme: UIThemeBean) {
      theme.ui = importMapFromParentTheme(theme.ui, parentTheme.ui)
      theme.icons = importMapFromParentTheme(theme.icons, parentTheme.icons)
      theme.background = importMapFromParentTheme(theme.background, parentTheme.background)
      theme.emptyFrameBackground = importMapFromParentTheme(theme.emptyFrameBackground, parentTheme.emptyFrameBackground)
      theme.colors = importMapFromParentTheme(theme.colors, parentTheme.colors)
      theme.iconColorsOnSelection = importMapFromParentTheme(theme.iconColorsOnSelection, parentTheme.iconColorsOnSelection)
    }

    private fun importMapFromParentTheme(themeMap: MutableMap<String, Any?>?, parentThemeMap: Map<String, Any?>?): MutableMap<String, Any?>? {
      if (parentThemeMap == null) {
        return themeMap
      }

      val result = LinkedHashMap(parentThemeMap)
      if (themeMap != null) {
        for ((key, value) in themeMap) {
          result.remove(key)
          result.put(key, value)
        }
      }
      return result
    }
  }

  @Transient
  @JvmField
  var providerClassLoader: ClassLoader? = null

  @JvmField
  var name: String? = null
  @JvmField
  var dark = false
  @JvmField
  var author: String? = null
  @JvmField
  var id: String? = null
  @JvmField
  var editorScheme: String? = null
  @JvmField
  var parentTheme: String? = null
  @JvmField
  var additionalEditorSchemes: Array<String>? = null
  @JvmField
  var ui: MutableMap<String, Any?>? = null
  @JvmField
  var icons: MutableMap<String, Any?>? = null

  @JvmField
  @Transient
  var patcher: IconPathPatcher? = null

  @JvmField
  var background: MutableMap<String, Any?>? = null
  @JvmField
  var emptyFrameBackground: MutableMap<String, Any?>? = null
  @JvmField
  var colors: MutableMap<String, Any?>? = null
  @JvmField
  var iconColorsOnSelection: MutableMap<String, Any?>? = null

  @JvmField
  var editorSchemeName: String? = null

  @JvmField
  @Transient
  var colorPatcher: SvgElementColorPatcherProvider? = null
  @JvmField
  @Transient
  var selectionColorPatcher: SvgElementColorPatcherProvider? = null

  @JvmField
  var resourceBundle: String? = "messages.IdeBundle"
  @JvmField
  var nameKey: String? = null
}