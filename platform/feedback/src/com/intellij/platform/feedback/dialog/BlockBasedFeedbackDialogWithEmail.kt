// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.dialog

import com.intellij.openapi.project.Project
import com.intellij.platform.feedback.dialog.uiBlocks.EmailBlock
import com.intellij.platform.feedback.dialog.uiBlocks.TextDescriptionProvider
import com.intellij.platform.feedback.impl.DEFAULT_FEEDBACK_CONSENT_ID
import com.intellij.platform.feedback.impl.FeedbackRequestDataWithDetailedAnswer
import com.intellij.platform.feedback.impl.FeedbackRequestType
import com.intellij.platform.feedback.impl.submitFeedback
import com.intellij.ui.dsl.builder.Panel
import kotlinx.serialization.json.JsonObject

/** This number should be increased when [BlockBasedFeedbackDialogWithEmail] fields changing */
const val BLOCK_BASED_FEEDBACK_WITH_EMAIL_VERSION = 1

abstract class BlockBasedFeedbackDialogWithEmail<T : SystemDataJsonSerializable>(
  myProject: Project?, forTest: Boolean) : BlockBasedFeedbackDialog<T>(myProject, forTest) {

  override val myFeedbackJsonVersion: Int = super.myFeedbackJsonVersion + BLOCK_BASED_FEEDBACK_WITH_EMAIL_VERSION
  abstract val zendeskTicketTitle: String
  abstract val zendeskFeedbackType: String

  protected val emailBlockWithAgreement = EmailBlock(myProject) { myShowFeedbackSystemInfoDialog() }

  protected open fun shouldAutoCloseZendeskTicket(): Boolean {
    return true
  }

  /**
   * Computes Zendesk ticket tags based on the collected data.
   *
   * Zendesk's tags have restrictions:
   * * You can use only alphanumeric, dash, underscore, colon, and the forward slash characters.
   * * You can't use special characters, such as #, @, or ! in tags.
   *   If you try to add tags with special characters, they disappear when the ticket is updated.
   */
  protected open fun computeZendeskTicketTags(collectedData: JsonObject): List<String> {
    return emptyList()
  }

  override fun sendFeedbackData() {
    val collectedData = collectDataToJsonObject()
    val zendeskTicketTags = computeZendeskTicketTags(collectedData)

    val feedbackData = FeedbackRequestDataWithDetailedAnswer(
      emailBlockWithAgreement.getEmailAddressIfSpecified(),
      zendeskTicketTitle,
      collectDataToPlainText(),
      DEFAULT_FEEDBACK_CONSENT_ID,
      shouldAutoCloseZendeskTicket(),
      zendeskTicketTags,
      zendeskFeedbackType,
      collectedData
    )
    submitFeedback(feedbackData,
                   { showThanksNotification() },
                   { },
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