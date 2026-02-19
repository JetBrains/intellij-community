// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.ui.command.line

import com.intellij.execution.ui.SettingsEditorFragmentType
import com.intellij.openapi.externalSystem.service.ui.util.SettingsFragmentInfo
import com.intellij.openapi.util.NlsContexts

interface CommandLineInfo : SettingsFragmentInfo {
  override val settingsId: String get() = "external.system.command.line.fragment"
  override val settingsGroup: String? get() = null
  override val settingsPriority: Int get() = 100
  override val settingsType get() = SettingsEditorFragmentType.COMMAND_LINE
  override val settingsActionHint: String? get() = null

  val dialogTitle: @NlsContexts.DialogTitle String
  val dialogTooltip: @NlsContexts.Tooltip String

  val fieldEmptyState: @NlsContexts.StatusText String

  val tablesInfo: List<CompletionTableInfo>
}
