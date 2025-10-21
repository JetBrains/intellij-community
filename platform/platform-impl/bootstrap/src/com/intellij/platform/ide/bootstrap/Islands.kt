// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.bootstrap

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.laf.UiThemeProviderListManager
import com.intellij.ide.util.PropertiesComponent
import com.intellij.idea.AppMode
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.extensions.PluginId
import com.intellij.ui.JBColor
import com.intellij.util.PlatformUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
suspend fun applyIslandsTheme(afterImportSettings: Boolean) {
  val app = ApplicationManager.getApplication()
  if (app.isUnitTestMode || app.isHeadlessEnvironment || AppMode.isRemoteDevHost()) {
    return
  }

  if (System.getProperty("platform.experiment.ab.manual.option", "") == "control.option") {
    return
  }

  val properties = serviceAsync<PropertiesComponent>()

  if (PlatformUtils.isRider() && (!properties.getBoolean("rider.color.scheme.updated", false) || afterImportSettings)) {
    if (!afterImportSettings) {
      properties.setValue("rider.color.scheme.updated", true)
    }

    val finish = withContext(Dispatchers.EDT) {
      changeColorSchemeForRiderIslandsDarkTheme(afterImportSettings)
    }
    if (finish) {
      return
    }
  }

  if (afterImportSettings) {
    if (properties.getValue("ide.islands.show.feedback3") != "done") {
      return
    }
  }
  else if (properties.getBoolean("ide.islands.ab3", false)) {
    return
  }

  properties.setValue("ide.islands.ab3", true)

  withContext(Dispatchers.EDT) {
    enableTheme()
  }
}

private suspend fun enableTheme() {
  val lafManager = serviceAsync<LafManager>()

  val currentTheme = lafManager.currentUIThemeLookAndFeel?.id ?: return
  if (currentTheme != "ExperimentalDark" && currentTheme != "ExperimentalLight" && currentTheme != "ExperimentalLightWithLightHeader") {
    return
  }

  val colorsManager = EditorColorsManager.getInstance()
  val currentEditorTheme = colorsManager.globalScheme.displayName
  if (currentEditorTheme != "Light" && currentEditorTheme != "Dark" && currentEditorTheme != "Rider Light" && currentEditorTheme != "Rider Dark") {
    return
  }

  val id = PluginId.getId("com.chrisrm.idea.MaterialThemeUI")
  if (PluginManagerCore.findPlugin(id) != null && !PluginManagerCore.isDisabled(id)) {
    return
  }

  val isLight = JBColor.isBright()

  val newTheme = UiThemeProviderListManager.getInstance().findThemeById(if (isLight) "Islands Light" else "Islands Dark") ?: return

  PropertiesComponent.getInstance().setValue("ide.islands.show.feedback3", "done")

  lafManager.setCurrentLookAndFeel(newTheme, true)

  val editorScheme = if (PlatformUtils.isRider()) {
    if (isLight) "Rider Light" else "Rider Islands Dark"
  }
  else {
    if (isLight) "Light" else "Islands Dark"
  }

  newTheme.installEditorScheme(colorsManager.getScheme(editorScheme) ?: colorsManager.defaultScheme)

  lafManager.updateUI()
}

private suspend fun changeColorSchemeForRiderIslandsDarkTheme(afterImportSettings: Boolean): Boolean {
  val colorsManager = EditorColorsManager.getInstance()
  val lafManager = serviceAsync<LafManager>()
  val currentLaf = lafManager.currentUIThemeLookAndFeel ?: return false

  val colorScheme = if (afterImportSettings) "Islands Dark" else "Rider Dark"

  if (currentLaf.id != "Islands Dark" || colorsManager.globalScheme.displayName != colorScheme) {
    return false
  }

  currentLaf.installEditorScheme(colorsManager.getScheme("Rider Islands Dark") ?: return false)

  lafManager.updateUI()

  return true
}