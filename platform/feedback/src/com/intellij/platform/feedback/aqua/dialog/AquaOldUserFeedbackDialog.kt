// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.aqua.dialog

import com.intellij.openapi.project.Project
import com.intellij.platform.feedback.aqua.bundle.AquaFeedbackBundle
import com.intellij.platform.feedback.dialog.BlockBasedFeedbackDialogWithEmail
import com.intellij.platform.feedback.dialog.CommonFeedbackSystemData
import com.intellij.platform.feedback.dialog.showFeedbackSystemInfoDialog
import com.intellij.platform.feedback.dialog.uiBlocks.*
import com.intellij.platform.feedback.impl.notification.ThanksForFeedbackNotification
import javax.swing.Action

class AquaOldUserFeedbackDialog(
  project: Project?,
  forTest: Boolean
) : BlockBasedFeedbackDialogWithEmail<CommonFeedbackSystemData>(project, forTest) {

  /** Increase the additional number when feedback format is changed */
  override val myFeedbackJsonVersion: Int = super.myFeedbackJsonVersion + 1

  override val zendeskTicketTitle: String = "Aqua in-IDE Feedback"
  override val zendeskFeedbackType: String = "Aqua Old User in-IDE Feedback"
  override val myFeedbackReportId: String = "aqua_old_user_feedback"
  override val myTitle: String = AquaFeedbackBundle.message("old.user.dialog.top.title")
  override val myBlocks: List<FeedbackBlock> = listOf(
    TopLabelBlock(AquaFeedbackBundle.message("old.user.dialog.title")),
    DescriptionBlock(AquaFeedbackBundle.message("old.user.dialog.description")),
    RatingBlock(AquaFeedbackBundle.message("old.user.dialog.satisfaction.label"), "satisfaction"),
    TextAreaBlock(AquaFeedbackBundle.message("old.user.dialog.like_most.label"), "like_most"),
    TextAreaBlock(AquaFeedbackBundle.message("old.user.dialog.problems.label"), "problems_or_missing_features"),
  )
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
    ThanksForFeedbackNotification(description = AquaFeedbackBundle.message(
      "new.user.notification.thanks.feedback.content")).notify(myProject)
  }


  override fun getCancelAction(): Action {
    val cancelAction = super.getCancelAction()
    cancelAction.putValue(Action.NAME, AquaFeedbackBundle.message("old.user.dialog.cancel.label"))
    return cancelAction
  }
}