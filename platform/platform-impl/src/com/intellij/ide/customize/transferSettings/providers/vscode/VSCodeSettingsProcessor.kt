// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import java.io.IOException
import java.nio.file.Files
import java.time.Duration
import java.time.Instant

class VSCodeSettingsProcessor {
  companion object {
    private val homeDirectory = System.getProperty("user.home")

    internal val vsCodeHome: String = when {
      SystemInfo.isMac -> "$homeDirectory/Library/Application Support/Code"
      SystemInfo.isWindows -> "${WindowsEnvVariables.applicationData}/Code"
      else -> "$homeDirectory/.config/Code"
    }

    internal val storageFile: File = File("$vsCodeHome/storage.json")
    internal val rapidRenderFile: File = File("$vsCodeHome/rapid_render.json")
    internal val keyBindingsFile: File = File("$vsCodeHome/User/keybindings.json")
    internal val generalSettingsFile: File = File("$vsCodeHome/User/settings.json")
    internal val pluginsDirectory: File = File("$homeDirectory/.vscode/extensions")
    internal val database: File = File("$vsCodeHome/User/globalStorage/state.vscdb")

    fun getDefaultSettings(): Settings = Settings(
      laf = KnownLafs.Darcula,
      syntaxScheme = KnownColorSchemes.Darcula,
      keymap = if (SystemInfoRt.isMac) KnownKeymaps.VSCodeMac else KnownKeymaps.VSCode
    )

    private val timeAfterLastModificationToConsiderTheInstanceRecent = Duration.ofHours(365 * 24) // one year
  }

  fun willDetectAtLeastSomething(): Boolean = keyBindingsFile.exists() || pluginsDirectory.exists() || storageFile.exists() || generalSettingsFile.exists()

  fun isInstanceRecentEnough(): Boolean {
    try {
      val fileToCheck = database
      if (fileToCheck.exists()) {
        val time = Files.getLastModifiedTime(fileToCheck.toPath())
        return time.toInstant() > Instant.now() - timeAfterLastModificationToConsiderTheInstanceRecent
      }

      return false
    }
    catch (_: IOException) {
      return false
    }
  }

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