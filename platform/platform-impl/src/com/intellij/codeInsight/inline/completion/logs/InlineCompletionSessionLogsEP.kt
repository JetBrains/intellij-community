// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.logs

import com.intellij.codeInsight.inline.completion.logs.InlineCompletionLogsContainer.Phase
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.openapi.extensions.ExtensionPointName

interface InlineCompletionSessionLogsEP {

  val fields: List<Pair<Phase, EventField<*>>>

  companion object {
    val EP_NAME = ExtensionPointName<InlineCompletionSessionLogsEP>("com.intellij.inline.completion.session.logs");
  }
}