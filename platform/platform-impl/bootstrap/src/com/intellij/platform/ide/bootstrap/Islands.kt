// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.bootstrap

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.laf.UiThemeProviderListManager
import com.intellij.ide.util.PropertiesComponent
import com.intellij.idea.AppMode
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.Experiments
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.JBColor
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun applyIslandsTheme(afterImportSettings: Boolean) {
  val application = ApplicationManager.getApplication()
  if (Registry.`is`("llm.riderNext.enabled", false) || !application.isEAP || application.isUnitTestMode || application.isHeadlessEnvironment || AppMode.isRemoteDevHost()) {
    return
  }

  val properties = PropertiesComponent.getInstance()
  if (afterImportSettings) {
    if (properties.getValue("ide.islands.show.feedback") != "show.promo") {
      return
    }
  }
  else if (properties.getBoolean("ide.islands.ab", false)) {
    return
  }

  properties.setValue("ide.islands.ab", true)

  val experiments = Experiments.getInstance()
  if (experiments.isFeatureEnabled("ide.one.island.theme") || System.getProperty("ide.one.island.theme") != null) {
    enableTheme(true)
  }
  else if (experiments.isFeatureEnabled("ide.many.islands.theme") || System.getProperty("ide.many.islands.theme") != null) {
    enableTheme(false)
  }
}

private fun enableTheme(oneIsland: Boolean) {
  val lafManager = LafManager.getInstance()
  val colorsManager = EditorColorsManager.getInstance()

  val currentTheme = lafManager.currentUIThemeLookAndFeel?.id ?: return
  val currentEditorTheme = colorsManager.globalScheme.displayName

  if (lafManager.autodetect) {
    return
  }

  if ((currentTheme != "ExperimentalDark" && currentTheme != "ExperimentalLight" && currentTheme != "ExperimentalLightWithLightHeader") ||
      (currentEditorTheme != "Light" && currentEditorTheme != "Dark" && currentEditorTheme != "Rider Light" && currentEditorTheme != "Rider Dark")) {
    return
  }

  val id = PluginId.getId("com.chrisrm.idea.MaterialThemeUI")
  if (PluginManagerCore.findPlugin(id) != null && !PluginManagerCore.isDisabled(id)) {
    return
  }

  val uiThemeManager = UiThemeProviderListManager.getInstance()
  val isLight = JBColor.isBright()

  val editorScheme: String
  val newTheme = if (oneIsland) {
    editorScheme = if (isLight) "Light" else "Island Dark"
    uiThemeManager.findThemeById(if (isLight) "One Island Light" else "One Island Dark")
  }
  else {
    editorScheme = if (isLight) "Light" else "Island Dark"
    uiThemeManager.findThemeById(if (isLight) "Many Islands Light" else "Many Islands Dark")
  }

  if (newTheme == null) {
    return
  }

  PropertiesComponent.getInstance().setValue("ide.islands.show.feedback", "show.promo")

  lafManager.setCurrentLookAndFeel(newTheme, true)

  newTheme.installEditorScheme(colorsManager.getScheme(editorScheme) ?: colorsManager.defaultScheme)

  lafManager.updateUI()
}