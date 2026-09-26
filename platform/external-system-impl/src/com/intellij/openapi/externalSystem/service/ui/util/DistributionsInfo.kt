// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.ui.util

import com.intellij.openapi.roots.ui.distribution.DistributionInfo
import com.intellij.openapi.roots.ui.distribution.FileChooserInfo
import org.jetbrains.annotations.Nls

interface DistributionsInfo : FileChooserInfo, LabeledSettingsFragmentInfo {
  override val settingsId: String get() = "external.system.distribution.fragment"
  override val settingsGroup: String? get() = null
  override val settingsActionHint: String? get() = null

  override val fileChooserMacroFilter get() = FileChooserInfo.DIRECTORY_PATH

  val comboBoxActionName: @Nls(capitalization = Nls.Capitalization.Sentence) String

  val distributions: List<DistributionInfo>
}