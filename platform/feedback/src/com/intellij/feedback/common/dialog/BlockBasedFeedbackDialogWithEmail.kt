// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback.common.dialog

import com.intellij.feedback.common.DEFAULT_FEEDBACK_CONSENT_ID
import com.intellij.feedback.common.FeedbackRequestDataWithDetailedAnswer
import com.intellij.feedback.common.FeedbackRequestType
import com.intellij.feedback.common.dialog.uiBlocks.EmailBlock
import com.intellij.feedback.common.dialog.uiBlocks.FeedbackBlock
import com.intellij.feedback.common.dialog.uiBlocks.TextDescriptionProvider
import com.intellij.feedback.common.submitFeedback
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.dsl.builder.Panel

abstract class BlockBasedFeedbackDialogWithEmail(@NlsContexts.DialogTitle myTitle: String,
                                                 myBlocks: List<FeedbackBlock>,
                                                 myProject: Project?,
                                                 forTest: Boolean) : BlockBasedFeedbackDialog(myTitle, myBlocks, myProject, forTest) {

  abstract val zendeskTicketTitle: String
  abstract val zendeskFeedbackType: String

  //TODO: Make this lambda generic
  private val emailBlockWithAgreement = EmailBlock(myProject) { showFeedbackSystemInfoDialog(myProject, systemInfoData) }
  override fun sendFeedbackData() {
    //TODO: Add updating settings, maybe to IdleFeedbackTypes
    //AquaNewUserFeedbackService.getInstance().state.feedbackSent = true
    //TODO: What if email address is empty?
    val feedbackData = FeedbackRequestDataWithDetailedAnswer(""/*emailBlockWithAgreement.getEmailAddressIfSpecified()*/,
                                                             zendeskTicketTitle,
                                                             createRequestDescription(),
                                                             DEFAULT_FEEDBACK_CONSENT_ID,
                                                             zendeskFeedbackType,
                                                             createCollectedDataJsonObject())
    //TODO: Add parameter to send thank you notification
    submitFeedback(myProject, feedbackData,
                   { }, { },
                   if (forTest) FeedbackRequestType.TEST_REQUEST else FeedbackRequestType.PRODUCTION_REQUEST)
  }

  override fun addFooterToPanel(panel: Panel) {
    emailBlockWithAgreement.addToPanel(panel)
  }

  private fun createRequestDescription(): String {
    val stringBuilder = StringBuilder()

    for (block in myBlocks) {
      if (block is TextDescriptionProvider) {
        block.collectBlockTextDescription(stringBuilder)
      }
    }

    stringBuilder.appendLine()
    stringBuilder.appendLine()
    stringBuilder.appendLine(systemInfoData.toString())
    return stringBuilder.toString()
  }
}