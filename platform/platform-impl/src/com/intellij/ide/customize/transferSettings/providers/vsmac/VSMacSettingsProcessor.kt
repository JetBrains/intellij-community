package com.intellij.ide.customize.transferSettings.providers.vsmac

import com.intellij.ide.customize.transferSettings.db.KnownColorSchemes
import com.intellij.ide.customize.transferSettings.db.KnownKeymaps
import com.intellij.ide.customize.transferSettings.db.KnownLafs
import com.intellij.ide.customize.transferSettings.models.Settings
import com.intellij.ide.customize.transferSettings.providers.vsmac.parsers.GeneralSettingsParser
import com.intellij.ide.customize.transferSettings.providers.vsmac.parsers.KeyBindingsParser
import com.intellij.ide.customize.transferSettings.providers.vsmac.parsers.RecentProjectsParser
import java.io.File

class VSMacSettingsProcessor {
  companion object {
    private val homeDirectory = System.getProperty("user.home")

    private val vsHome = "$homeDirectory/Library/VisualStudio"
    internal val vsPreferences = "$homeDirectory/Library/Preferences/VisualStudio"

    internal fun getRecentlyUsedFile(version: String) = File("$vsPreferences/$version/RecentlyUsed.xml")
    internal fun getKeyBindingsFile(version: String) = File("$vsHome/$version/KeyBindings/Custom.mac-kb.xml")
    internal fun getGeneralSettingsFile(version: String) = File("$vsPreferences/$version/MonoDevelopProperties.xml")

    fun getDefaultSettings() = Settings(
      laf = KnownLafs.Light,
      syntaxScheme = KnownColorSchemes.Light,
      keymap = KnownKeymaps.VSMac
    )
  }

  fun getProcessedSettings(version: String): Settings {
    val keyBindingsFile = getKeyBindingsFile(version)
    val generalSettingsFile = getGeneralSettingsFile(version)
    val recentlyUsedFile = getRecentlyUsedFile(version)

    val settings = getDefaultSettings()
    KeyBindingsParser(settings).process(keyBindingsFile)
    GeneralSettingsParser(settings).process(generalSettingsFile)
    RecentProjectsParser(settings).process(recentlyUsedFile)

    return settings
  }
}