package com.intellij.ide.customize.transferSettings.providers.vscode

import com.intellij.ide.customize.transferSettings.db.KnownColorSchemes
import com.intellij.ide.customize.transferSettings.db.KnownKeymaps
import com.intellij.ide.customize.transferSettings.db.KnownLafs
import com.intellij.ide.customize.transferSettings.db.WindowsEnvVariables
import com.intellij.ide.customize.transferSettings.models.Settings
import com.intellij.ide.customize.transferSettings.providers.vscode.parsers.*
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.SystemInfoRt
import java.io.File

class VSCodeSettingsProcessor {
  companion object {
    private val homeDirectory = System.getProperty("user.home")

    internal val vsCodeHome = when {
      SystemInfo.isMac -> "$homeDirectory/Library/Application Support/Code"
      SystemInfo.isWindows -> "${WindowsEnvVariables.applicationData}/Code"
      else -> "$homeDirectory/.config/Code"
    }

    internal val storageFile = File("$vsCodeHome/storage.json")
    internal val rapidRenderFile = File("$vsCodeHome/rapid_render.json")
    internal val keyBindingsFile = File("$vsCodeHome/User/keybindings.json")
    internal val generalSettingsFile = File("$vsCodeHome/User/settings.json")
    internal val pluginsDirectory = File("$homeDirectory/.vscode/extensions")
    internal val database = File("$vsCodeHome/User/globalStorage/state.vscdb")

    fun getDefaultSettings() = Settings(
      laf = KnownLafs.Darcula,
      syntaxScheme = KnownColorSchemes.Darcula,
      keymap = if (SystemInfoRt.isMac) KnownKeymaps.VSCodeMac else KnownKeymaps.VSCode
    )
  }

  fun willDetectAtLeastSomething() = keyBindingsFile.exists() || pluginsDirectory.exists() || storageFile.exists() || generalSettingsFile.exists()

  fun getProcessedSettings(): Settings {
    val settings = getDefaultSettings()
    if (keyBindingsFile.exists()) {
      KeyBindingsParser(settings).process(keyBindingsFile)
    }
    if (pluginsDirectory.exists()) {
      PluginsParser(settings).process(pluginsDirectory)
    }
    if (storageFile.exists()) {
      StorageParser(settings).process(storageFile)
    }
    if (generalSettingsFile.exists()) {
      GeneralSettingsParser(settings).process(generalSettingsFile)
    }
    if (database.exists()) {
      StateDatabaseParser(settings).process(database)
    }

    return settings
  }

}