// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.ui.tasks.and.arguments

import com.intellij.openapi.externalSystem.service.ui.completetion.TextCompletionContributor.TextCompletionInfo
import com.intellij.openapi.util.NlsContexts
import org.jetbrains.annotations.Nls
import javax.swing.Icon

interface ExternalSystemTasksAndArgumentsInfo {
  val hint: @Nls String?
  val title: @NlsContexts.DialogTitle String
  val tooltip: @NlsContexts.Tooltip String
  val emptyState: @NlsContexts.StatusText String
  val name: @Nls(capitalization = Nls.Capitalization.Sentence) String

  val tablesInfo: List<CompletionTableInfo>

  interface CompletionTableInfo {
    val emptyState: @Nls String

    val dataIcon: Icon?
    val dataColumnName: @NlsContexts.ColumnName String

    val descriptionColumnName: @NlsContexts.ColumnName String

    val completionInfo: List<TextCompletionInfo>
    val tableCompletionInfo: List<TextCompletionInfo>
  }
}
