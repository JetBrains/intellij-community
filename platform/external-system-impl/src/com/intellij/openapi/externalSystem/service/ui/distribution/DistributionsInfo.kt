// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.ui.distribution

import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.util.NlsContexts
import org.jetbrains.annotations.Nls

interface DistributionsInfo {
  val settingsName: @Nls(capitalization = Nls.Capitalization.Sentence) String?
  val settingsGroup: @Nls(capitalization = Nls.Capitalization.Title) String?
  val settingsPriority: Int
  val settingsHint: @Nls String?
  val settingsActionHint: @Nls String?

  val comboBoxPreferredWidth: Int?
  val comboBoxActionName: @Nls(capitalization = Nls.Capitalization.Sentence) String

  val fileChooserTitle: @NlsContexts.DialogTitle String?
  val fileChooserDescription: @NlsContexts.Label String?
  val fileChooserDescriptor: FileChooserDescriptor

  val distributions: List<DistributionInfo>
}