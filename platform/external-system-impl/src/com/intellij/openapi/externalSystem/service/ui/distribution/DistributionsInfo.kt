// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.ui.distribution

import com.intellij.execution.ui.SettingsEditorFragmentType
import com.intellij.openapi.externalSystem.service.ui.util.FileChooserInfo
import com.intellij.openapi.externalSystem.service.ui.util.SettingsFragmentInfo
import org.jetbrains.annotations.Nls

interface DistributionsInfo : FileChooserInfo, SettingsFragmentInfo {
  override val settingsId: String get() = "external.system.distribution.fragment"
  override val settingsGroup: String? get() = null
  override val settingsPriority: Int get() = 90
  override val settingsType get() = SettingsEditorFragmentType.COMMAND_LINE
  override val settingsActionHint: String? get() = null

  override val fileChooserMacroFilter get() = FileChooserInfo.DIRECTORY_PATH

  val comboBoxPreferredWidth: Int?
  val comboBoxActionName: @Nls(capitalization = Nls.Capitalization.Sentence) String

  val distributions: List<DistributionInfo>
}