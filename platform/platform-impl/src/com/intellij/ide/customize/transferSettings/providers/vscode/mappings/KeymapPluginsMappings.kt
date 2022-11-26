package com.intellij.ide.customize.transferSettings.providers.vscode.mappings

import com.intellij.ide.customize.transferSettings.models.Keymap
import com.intellij.openapi.util.SystemInfoRt

@Suppress("FunctionName")
object KeymapPluginsMappings {
  fun map(id: String): Keymap? {
    return when (id) {
      //"k--kato.intellij-idea-keybindings" -> SystemDep(KnownKeymaps.IntelliJMacOS, KnownKeymaps.IntelliJ)
      else -> null
    }
  }

  private fun SystemDep(mac: Keymap, other: Keymap) = if (SystemInfoRt.isMac) mac else other
}