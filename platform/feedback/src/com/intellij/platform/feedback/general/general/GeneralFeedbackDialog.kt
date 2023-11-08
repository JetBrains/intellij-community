// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.general.general


import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.project.Project
import com.intellij.platform.feedback.dialog.BlockBasedFeedbackDialogWithEmail
import com.intellij.platform.feedback.dialog.CommonFeedbackSystemData
import com.intellij.platform.feedback.dialog.showFeedbackSystemInfoDialog
import com.intellij.platform.feedback.dialog.uiBlocks.FeedbackBlock
import com.intellij.platform.feedback.dialog.uiBlocks.TextAreaBlock
import com.intellij.platform.feedback.dialog.uiBlocks.TopLabelBlock
import com.intellij.platform.feedback.impl.notification.ThanksForFeedbackNotification
import com.intellij.ui.dsl.builder.BottomGap
import javax.swing.Action

internal class GeneralFeedbackDialog(project: Project?,
                                     forTest: Boolean
) : BlockBasedFeedbackDialogWithEmail<CommonFeedbackSystemData>(project, forTest) {

  /** Increase the additional number when feedback format is changed */
  override val myFeedbackJsonVersion: Int = super.myFeedbackJsonVersion + 3

  override val zendeskTicketTitle: String = "${ApplicationNamesInfo.getInstance().productName} General Feedback"
  override val zendeskFeedbackType: String = "General Feedback"
  override val myFeedbackReportId: String = "general_feedback"

  override val myTitle: String = GeneralFeedbackBundle.message("general.dialog.top.title")

  private val tellUsMoreJsonElementName = "tell_us_more"

  override val myBlocks: List<FeedbackBlock> = mutableListOf<FeedbackBlock>().apply {
    add(TopLabelBlock(GeneralFeedbackBundle.message("general.dialog.title")).setBottomGap(BottomGap.MEDIUM))
    add(
      TextAreaBlock(
        GeneralFeedbackBundle.message("general.dialog.text.area.details"), tellUsMoreJsonElementName
      ).requireNotEmpty(GeneralFeedbackBundle.message("general.dialog.text.area.details.require"))
    )
  }

  override val mySystemInfoData: CommonFeedbackSystemData by lazy {
    CommonFeedbackSystemData.getCurrentData()
  }
  override val myShowFeedbackSystemInfoDialog: () -> Unit = {
    showFeedbackSystemInfoDialog(myProject, mySystemInfoData)
  }

  init {
    init()
  }

  override fun showThanksNotification() {
    ThanksForFeedbackNotification(description = GeneralFeedbackBundle.message(
      "general.notification.thanks.feedback.content", ApplicationNamesInfo.getInstance().fullProductName)).notify(myProject)
  }

  override fun getCancelAction(): Action {
    val cancelAction = super.getCancelAction()
    cancelAction.putValue(Action.NAME, GeneralFeedbackBundle.message("general.dialog.cancel.label"))
    return cancelAction
  }

  override fun shouldAutoCloseZendeskTicket(): Boolean {
    return false
  }
}