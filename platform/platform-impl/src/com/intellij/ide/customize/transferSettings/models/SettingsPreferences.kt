// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.customize.transferSettings.models

enum class SettingsPreferencesKind {
  Laf, SyntaxScheme, Keymap, RecentProjects, Plugins, None;

  companion object {
    private val noneList = listOf(None)
    val keysWithoutNone = values().toList() - noneList
  }
}

data class SettingsPreferences(
  var laf: Boolean = true,
  var syntaxScheme: Boolean = true,
  var keymap: Boolean = true,
  var otherSettings: Boolean = true,
  var recentProjects: Boolean = true,
  var plugins: Boolean = true
) {
  operator fun set(index: SettingsPreferencesKind, value: Boolean) {
    when (index) {
      SettingsPreferencesKind.Laf -> laf = value
      SettingsPreferencesKind.SyntaxScheme -> syntaxScheme = value
      SettingsPreferencesKind.Keymap -> keymap = value
      SettingsPreferencesKind.RecentProjects -> recentProjects = value
      SettingsPreferencesKind.Plugins -> plugins = value
      SettingsPreferencesKind.None -> {}
    }
  }

  operator fun get(index: SettingsPreferencesKind): Boolean {
    return when (index) {
      SettingsPreferencesKind.Laf -> laf
      SettingsPreferencesKind.SyntaxScheme -> syntaxScheme
      SettingsPreferencesKind.Keymap -> keymap
      SettingsPreferencesKind.RecentProjects -> recentProjects
      SettingsPreferencesKind.Plugins -> plugins
      SettingsPreferencesKind.None -> false
    }
  }

  fun toList(settings: Settings): List<Pair<SettingsPreferencesKind, Boolean>> {
    return listOf(
      SettingsPreferencesKind.Laf to laf,
      SettingsPreferencesKind.SyntaxScheme to syntaxScheme,
      SettingsPreferencesKind.Keymap to keymap,
      SettingsPreferencesKind.RecentProjects to (recentProjects && settings.recentProjects.isNotEmpty()),
      SettingsPreferencesKind.Plugins to (plugins && settings.plugins.isNotEmpty())
    )
  }

  fun toListOfTrue(settings: Settings) = toList(settings).filter { it.second }.map { it.first }
}