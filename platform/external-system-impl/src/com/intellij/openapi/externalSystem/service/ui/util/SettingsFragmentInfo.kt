// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.ui.util

import com.intellij.execution.ui.SettingsEditorFragmentType
import org.jetbrains.annotations.Nls

interface SettingsFragmentInfo {
  val settingsId: String
  val settingsName: @Nls(capitalization = Nls.Capitalization.Sentence) String?
  val settingsGroup: @Nls(capitalization = Nls.Capitalization.Title) String?
  val settingsPriority: Int
  val settingsType: SettingsEditorFragmentType
  val settingsHint: @Nls String?
  val settingsActionHint: @Nls String?
}