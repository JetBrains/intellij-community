// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.ui.command.line

import com.intellij.openapi.util.NlsContexts
import org.jetbrains.annotations.Nls

interface CommandLineInfo {
  val settingsName: @Nls(capitalization = Nls.Capitalization.Sentence) String?
  val settingsGroup: @Nls(capitalization = Nls.Capitalization.Title) String?
  val settingsPriority: Int
  val settingsHint: @Nls String?
  val settingsActionHint: @Nls String?

  val dialogTitle: @NlsContexts.DialogTitle String
  val dialogTooltip: @NlsContexts.Tooltip String

  val fieldEmptyState: @NlsContexts.StatusText String

  val tablesInfo: List<CompletionTableInfo>
}
