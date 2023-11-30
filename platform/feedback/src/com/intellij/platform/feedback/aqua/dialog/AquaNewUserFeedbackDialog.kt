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

class AquaNewUserFeedbackDialog(
  project: Project?,
  forTest: Boolean
) : BlockBasedFeedbackDialogWithEmail<CommonFeedbackSystemData>(project, forTest) {

  /** Increase the additional number when feedback format is changed */
  override val myFeedbackJsonVersion: Int = super.myFeedbackJsonVersion + 1

  override val zendeskTicketTitle: String = "Aqua in-IDE Feedback"
  override val zendeskFeedbackType: String = "Aqua New User in-IDE Feedback"
  override val myFeedbackReportId: String = "aqua_new_user_feedback"

  override val myTitle: String = AquaFeedbackBundle.message("new.user.dialog.top.title")
  private val primaryTestingTargetsJsonElementName: List<String> = listOf("web_applications", "mobile_applications", "desktop_applications")
  private val dailyTasksJsonElementName: List<String> = listOf("automated_test_development_ui", "automated_test_development_non_ui",
                                                               "ui_test_development", "api_test_development",
                                                               "test_case_design_or_test_management",
                                                               "manual_testing")
  override val myBlocks: List<FeedbackBlock> = listOf(
    TopLabelBlock(AquaFeedbackBundle.message("new.user.dialog.title")),
    DescriptionBlock(AquaFeedbackBundle.message("new.user.dialog.description")),
    CheckBoxGroupBlock(
      AquaFeedbackBundle.message("new.user.dialog.primary.tasks.group.label"),
      List(6) {
        CheckBoxItemData(
          AquaFeedbackBundle.message("new.user.dialog.primary.tasks.${it}.label"),
          dailyTasksJsonElementName[it]
        )
      },
      "primary_daily_tasks"
    ).addOtherTextField(),
    ComboBoxBlock(
      AquaFeedbackBundle.message("new.user.dialog.team.size.label"),
      List(5) {
        AquaFeedbackBundle.message("new.user.dialog.team.size.${it}.label")
      },
      "project_team_size"
    ).addComment(AquaFeedbackBundle.message("new.user.dialog.team.size.bottom.label.left")).setColumnSize(15),
    CheckBoxGroupBlock(
      AquaFeedbackBundle.message("new.user.dialog.primary.testing.targets.group.label"),
      List(3) {
        CheckBoxItemData(
          AquaFeedbackBundle.message("new.user.dialog.primary.testing.targets.${it}.label"),
          primaryTestingTargetsJsonElementName[it]
        )
      },
      "primary_testing_targets"
    ).addOtherTextField(),
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
    cancelAction.putValue(Action.NAME, AquaFeedbackBundle.message("new.user.dialog.cancel.label"))
    return cancelAction
  }
}