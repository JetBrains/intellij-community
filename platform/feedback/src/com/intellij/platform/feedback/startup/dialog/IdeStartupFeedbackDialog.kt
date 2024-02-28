// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.startup.dialog

import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.project.Project
import com.intellij.platform.feedback.dialog.BlockBasedFeedbackDialogWithEmail
import com.intellij.platform.feedback.dialog.CommonFeedbackSystemData
import com.intellij.platform.feedback.dialog.showFeedbackSystemInfoDialog
import com.intellij.platform.feedback.dialog.uiBlocks.CheckBoxItemData
import com.intellij.platform.feedback.dialog.uiBlocks.ComboBoxBlock
import com.intellij.platform.feedback.dialog.uiBlocks.FeedbackBlock
import com.intellij.platform.feedback.dialog.uiBlocks.TopLabelBlock
import com.intellij.platform.feedback.impl.notification.ThanksForFeedbackNotification
import com.intellij.platform.feedback.startup.bundle.IdeStartupFeedbackMessagesBundle
import com.intellij.ui.dsl.builder.BottomGap

class IdeStartupFeedbackDialog(
  project: Project?,
  forTest: Boolean,
) : BlockBasedFeedbackDialogWithEmail<CommonFeedbackSystemData>(project, forTest) {

  /** Increase the additional number when feedback format is changed */
  override val myFeedbackJsonVersion: Int = super.myFeedbackJsonVersion + 1

  override val zendeskTicketTitle: String = "${ApplicationNamesInfo.getInstance().fullProductName} Startup Feedback"
  override val zendeskFeedbackType: String = "Startup Feedback"

  override val myFeedbackReportId: String = "startup_feedback"
  override val myTitle: String = IdeStartupFeedbackMessagesBundle.message("ide.startup.dialog.top.title")

  private val myOptionCountInColumn = 4
  private val myCheckBoxIdsForLastQuestion = listOf(
    "refactorings", "refactor_code", "code_generation", "intention_actions",
    "navigation_to_declaration_usages", "search_everywhere_for_class_method",
    "completion_of_already_indexed_classes_methods", "running_builds_tests"
  )

  override val myBlocks: List<FeedbackBlock> = listOf(
    TopLabelBlock(
      IdeStartupFeedbackMessagesBundle.message("ide.startup.dialog.title", ApplicationNamesInfo.getInstance().fullProductName)
    ).setBottomGap(BottomGap.MEDIUM),
    ComboBoxBlock(IdeStartupFeedbackMessagesBundle.message("ide.startup.dialog.question.1.label"),
                  List(5) { IdeStartupFeedbackMessagesBundle.message("ide.startup.dialog.question.1.option.${it + 1}") },
                  "feelings_while_waiting_ide_to_load").randomizeOptionOrder(),
    ComboBoxBlock(IdeStartupFeedbackMessagesBundle.message("ide.startup.dialog.question.2.label"),
                  List(5) { IdeStartupFeedbackMessagesBundle.message("ide.startup.dialog.question.2.option.${it + 1}") },
                  "rate_waiting_time"),
    ComboBoxBlock(IdeStartupFeedbackMessagesBundle.message("ide.startup.dialog.question.3.label"),
                  List(4) { IdeStartupFeedbackMessagesBundle.message("ide.startup.dialog.question.3.option.${it + 1}") },
                  "describes_you_the_best").setColumnSize(52),
    CustomCheckBoxGroupBlock(
      IdeStartupFeedbackMessagesBundle.message("ide.startup.dialog.question.4.label"),
      List(myOptionCountInColumn) {
        CheckBoxItemData(
          IdeStartupFeedbackMessagesBundle.message("ide.startup.dialog.question.4.option.${(it + 1)}"), myCheckBoxIdsForLastQuestion[it]
        ) to CheckBoxItemData(
          IdeStartupFeedbackMessagesBundle.message("ide.startup.dialog.question.4.option.${myOptionCountInColumn + (it + 1)}"),
          myCheckBoxIdsForLastQuestion[myOptionCountInColumn + it]
        )
      },
      CheckBoxItemData(IdeStartupFeedbackMessagesBundle.message("ide.startup.dialog.question.4.option.none"), "nothing"),
      "desirable_functionality_while_ide_not_ready"
    )
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
    ThanksForFeedbackNotification(description = IdeStartupFeedbackMessagesBundle.message(
      "ide.startup.notification.thanks.feedback.content", ApplicationNamesInfo.getInstance().fullProductName)).notify(myProject)
  }
}