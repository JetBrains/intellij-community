// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.dialog.uiBlocks

import com.intellij.openapi.project.Project
import com.intellij.platform.feedback.dialog.feedbackAgreement
import com.intellij.platform.feedback.impl.bundle.CommonFeedbackBundle
import com.intellij.ui.dsl.builder.Panel

class NoEmailAgreementBlock(private val myProject: Project?,
                            private val showFeedbackSystemInfoDialog: () -> Unit) : FeedbackBlock {

  override fun addToPanel(panel: Panel) {
    panel.apply {
      row {
        feedbackAgreement(myProject, CommonFeedbackBundle.message("dialog.feedback.consent.withoutEmail")) {
          showFeedbackSystemInfoDialog()
        }
      }
    }
  }
}