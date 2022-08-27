// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.customize.transferSettings.models

import com.intellij.ide.RecentProjectMetaInfo

open class Settings(
  val preferences: SettingsPreferences = SettingsPreferences(),

  var laf: ILookAndFeel? = null,
  var syntaxScheme: EditorColorScheme? = null,
  var keymap: Keymap? = null,
  val plugins: MutableList<FeatureInfo> = mutableListOf(),
  /**
   * Don't forget to set info.projectOpenTimestamp
   */
  val recentProjects: MutableList<RecentPathInfo> = mutableListOf()
)

data class RecentPathInfo(
  val path: String,
  val info: RecentProjectMetaInfo
)