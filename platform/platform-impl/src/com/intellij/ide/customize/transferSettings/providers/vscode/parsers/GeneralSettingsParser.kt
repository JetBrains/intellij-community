package com.intellij.ide.customize.transferSettings.providers.vscode.parsers

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeType
import com.fasterxml.jackson.databind.node.ObjectNode
import com.intellij.ide.customize.transferSettings.db.KnownLafs
import com.intellij.ide.customize.transferSettings.models.ILookAndFeel
import com.intellij.ide.customize.transferSettings.models.Settings
import com.intellij.ide.customize.transferSettings.models.SystemDarkThemeDetectorLookAndFeel
import com.intellij.ide.customize.transferSettings.providers.vscode.mappings.ThemesMappings
import com.intellij.openapi.diagnostic.logger
import java.io.File

private val logger = logger<GeneralSettingsParser>()

class GeneralSettingsParser(private val settings: Settings) {
  companion object {
    private const val COLOR_THEME = "workbench.colorTheme"
    private const val AUTODETECT_THEME = "window.autoDetectColorScheme"
    private const val PREFERRED_DARK_THEME = "workbench.preferredDarkColorTheme"
    private const val PREFERRED_LIGHT_THEME = "workbench.preferredLightColorTheme"
  }

  fun process(file: File) = try {
    logger.info("Processing a general settings file: $file")

    val root = ObjectMapper(JsonFactory().enable(JsonParser.Feature.ALLOW_COMMENTS)).readTree(file) as? ObjectNode
               ?: error("Unexpected JSON data; expected: ${JsonNodeType.OBJECT}")

    processThemeAndScheme(root)
    processAutodetectTheme(root)
  }
  catch (t: Throwable) {
    logger.warn(t)
  }

  private fun processThemeAndScheme(root: ObjectNode) {
    try {
      val theme = root[COLOR_THEME]?.textValue() ?: return
      val laf = ThemesMappings.themeMap(theme)
      settings.laf = laf
    }
    catch (t: Throwable) {
      logger.warn(t)
    }
  }

  private fun processAutodetectTheme(root: ObjectNode) {
    var lightLaf: ILookAndFeel? = null
    var darkLaf: ILookAndFeel? = null
    try {
      val preferredDarkTheme = root[PREFERRED_DARK_THEME]?.textValue() ?: return
      val laf = ThemesMappings.themeMap(preferredDarkTheme)
      darkLaf = laf
    }
    catch (t: Throwable) {
      logger.warn(t)
    }

    try {
      val preferredLightTheme = root[PREFERRED_LIGHT_THEME]?.textValue() ?: return
      val laf = ThemesMappings.themeMap(preferredLightTheme)
      lightLaf = laf
    }
    catch (t: Throwable) {
      logger.warn(t)
    }
    try {
      val autodetectTheme = root[AUTODETECT_THEME]?.booleanValue() ?: return
      if (autodetectTheme) {
        settings.laf = SystemDarkThemeDetectorLookAndFeel(darkLaf ?: KnownLafs.Darcula, lightLaf ?: KnownLafs.Light)
      }
    }
    catch (t: Throwable) {
      logger.warn(t)
    }
  }
}