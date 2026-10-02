// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeEditor.printing

import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DataSnapshot
import com.intellij.openapi.actionSystem.ExecutionDataKeys
import com.intellij.openapi.actionSystem.UiDataRule

internal class RunProfileNameForPrintingRule : UiDataRule {
  override fun uiDataSnapshot(sink: DataSink, snapshot: DataSnapshot) {
    sink.lazyValue(TextPrintHandler.FILE_NAME_FOR_PRINTING_KEY) { provider ->
      provider[ExecutionDataKeys.RUN_PROFILE]?.name
    }
  }
}