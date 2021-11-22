// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.ui.command.line

import com.intellij.openapi.externalSystem.service.ui.completion.TextCompletionInfo
import com.intellij.openapi.util.NlsContexts
import org.jetbrains.annotations.Nls
import javax.swing.Icon

interface CompletionTableInfo {
  val emptyState: @Nls String

  val dataColumnIcon: Icon?
  val dataColumnName: @NlsContexts.ColumnName String

  val descriptionColumnIcon: Icon?
  val descriptionColumnName: @NlsContexts.ColumnName String

  val completionInfo: List<TextCompletionInfo>
  val tableCompletionInfo: List<TextCompletionInfo>
}