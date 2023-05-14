// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback.common.dialog

import com.intellij.feedback.common.DEFAULT_FEEDBACK_CONSENT_ID
import com.intellij.feedback.common.FeedbackRequestDataWithDetailedAnswer
import com.intellij.feedback.common.FeedbackRequestType
import com.intellij.feedback.common.dialog.uiBlocks.EmailBlock
import com.intellij.feedback.common.dialog.uiBlocks.TextDescriptionProvider
import com.intellij.feedback.common.submitFeedback
import com.intellij.openapi.project.Project
import com.intellij.ui.dsl.builder.Panel

/** This number should be increased when [BlockBasedFeedbackDialogWithEmail] fields changing */
const val BLOCK_BASED_FEEDBACK_WITH_EMAIL_VERSION = 1

abstract class BlockBasedFeedbackDialogWithEmail<T : JsonSerializable>(myProject: Project?,
                                                                       forTest: Boolean) : BlockBasedFeedbackDialog<T>(myProject, forTest) {

  override val myFeedbackJsonVersion: Int = super.myFeedbackJsonVersion + BLOCK_BASED_FEEDBACK_WITH_EMAIL_VERSION
  abstract val zendeskTicketTitle: String
  abstract val zendeskFeedbackType: String

  private val emailBlockWithAgreement = EmailBlock(myProject) { myShowFeedbackSystemInfoDialog() }
  override fun sendFeedbackData() {
    val feedbackData = FeedbackRequestDataWithDetailedAnswer(
      emailBlockWithAgreement.getEmailAddressIfSpecified(),
      zendeskTicketTitle,
      collectDataToPlainText(),
      DEFAULT_FEEDBACK_CONSENT_ID,
      zendeskFeedbackType,
      collectDataToJsonObject()
    )
    submitFeedback(feedbackData,
                   { }, { },
                   if (myForTest) FeedbackRequestType.TEST_REQUEST else FeedbackRequestType.PRODUCTION_REQUEST)
  }

  override fun addFooterToPanel(panel: Panel) {
    emailBlockWithAgreement.addToPanel(panel)
  }

  private fun collectDataToPlainText(): String {
    val stringBuilder = StringBuilder()

    for (block in myBlocks) {
      if (block is TextDescriptionProvider) {
        block.collectBlockTextDescription(stringBuilder)
      }
    }

    stringBuilder.appendLine()
    stringBuilder.appendLine()
    stringBuilder.appendLine(mySystemInfoData.toString())
    return stringBuilder.toString()
  }
}