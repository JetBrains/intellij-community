// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.history.integration.ui.views

import com.intellij.history.integration.LocalHistoryBundle
import com.intellij.history.integration.ui.models.RevisionProcessingProgress
import com.intellij.openapi.progress.ProgressIndicator

internal class RevisionProcessingProgressAdapter(private val indicator: ProgressIndicator) : RevisionProcessingProgress {
  override fun processingLeftRevision() {
    indicator.text = LocalHistoryBundle.message("message.processing.left.revision")
  }

  override fun processingRightRevision() {
    indicator.text = LocalHistoryBundle.message("message.processing.right.revision")
  }

  override fun processed(percentage: Int) {
    indicator.fraction = percentage / 100.0
  }
}
