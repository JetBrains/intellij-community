package com.intellij.ide.customize.transferSettings.providers.vsmac.parsers

import com.intellij.ide.customize.transferSettings.db.KnownColorSchemes
import com.intellij.ide.customize.transferSettings.db.KnownLafs
import com.intellij.ide.customize.transferSettings.models.Settings
import com.intellij.ide.customize.transferSettings.providers.vsmac.mappings.SchemesMappings
import com.intellij.ide.customize.transferSettings.providers.vsmac.mappings.ThemesMappings
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.JDOMUtil
import org.jdom.Element
import java.io.File

private val logger = logger<GeneralSettingsParser>()

class GeneralSettingsParser(private val settings: Settings) {
  companion object {
    private const val KEY = "key"
    private const val VALUE = "value"
    private const val THEME = "MonoDevelop.Ide.UserInterfaceTheme"
    private const val SCHEME = "ColorScheme"
    private const val SCHEME_DARK = "ColorScheme-Dark"
  }

  private var currentLaf = settings.laf ?: KnownLafs.Light

  private val associatedScheme = mutableMapOf(
    KnownLafs.Light to KnownColorSchemes.Light,
    KnownLafs.Darcula to KnownColorSchemes.Darcula
  )

  fun process(file: File) = try {
    logger.info("Processing a file: $file")

    val root = JDOMUtil.load(file)

    processThemeAndScheme(root)
  }
  catch (t: Throwable) {
    logger.warn(t)
  }

  private fun processThemeAndScheme(root: Element) {
    try {
      root.children.forEach { element ->
        try {
          val attributes = element?.attributes ?: return@forEach

          if (!(attributes.size == 2 && attributes[0]?.name?.equals(KEY) == true && attributes[1]?.name?.equals(VALUE) == true)) {
            return@forEach
          }

          val settingId = attributes[0]?.value ?: return@forEach
          val value = attributes[1]?.value ?: return@forEach

          when (settingId) {
            THEME -> currentLaf = ThemesMappings.themeMap(value) ?: return@forEach
            SCHEME -> associatedScheme[KnownLafs.Light] = SchemesMappings.schemeMap(value) ?: return@forEach
            SCHEME_DARK -> associatedScheme[KnownLafs.Darcula] = SchemesMappings.schemeMap(value) ?: return@forEach
          }
        }
        catch (t: Throwable) {
          logger.warn(t)
        }
      }
      settings.laf = currentLaf
      settings.syntaxScheme = associatedScheme[currentLaf]
    }
    catch (t: Throwable) {
      logger.warn(t)
    }
  }
}