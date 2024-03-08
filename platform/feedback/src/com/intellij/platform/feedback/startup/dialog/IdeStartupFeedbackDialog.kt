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
import com.intellij.platform.feedback.startup.IdeStartupFeedbackCountCollector.logFeedbackFirstQuestionAnswer
import com.intellij.platform.feedback.startup.IdeStartupFeedbackCountCollector.logFeedbackForthQuestionAnswer
import com.intellij.platform.feedback.startup.IdeStartupFeedbackCountCollector.logFeedbackSecondQuestionAnswer
import com.intellij.platform.feedback.startup.IdeStartupFeedbackCountCollector.logFeedbackThirdQuestionAnswer
import com.intellij.platform.feedback.startup.bundle.IdeStartupFeedbackMessagesBundle
import com.intellij.ui.dsl.builder.BottomGap
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class IdeStartupFeedbackDialog(
  project: Project?,
  forTest: Boolean,
) : BlockBasedFeedbackDialogWithEmail<CommonFeedbackSystemData>(project, forTest) {

  companion object {
    internal val FIRST_QUESTION_OPTIONS = List(5) {
      IdeStartupFeedbackMessagesBundle.message("ide.startup.dialog.question.1.option.${it + 1}")
    }

    internal val SECOND_QUESTION_OPTIONS = List(5) {
      IdeStartupFeedbackMessagesBundle.message("ide.startup.dialog.question.2.option.${it + 1}")
    }

    internal val THIRD_QUESTION_OPTIONS = List(4) {
      IdeStartupFeedbackMessagesBundle.message("ide.startup.dialog.question.3.option.${it + 1}")
    }

    internal val COLLECTOR_THIRD_QUESTION_OPTIONS = listOf(
      "actively_wait_ide_load_and_try_to_interact_until_ready",
      "check_screen_while_IDE_loads_but_would_have_something_useful_to_do",
      "go_afk_while_ide_loads_but_would_rather_stay_and_have_something_useful_to_do",
      "go_afk_while_ide_loads_and_okay_with_not_looking_at_the_screen"
    )

    private val FORTH_QUESTION_OPTIONS = listOf(
      "refactorings", "refactor_code", "code_generation", "intention_actions",
      "navigation_to_declaration_usages", "search_everywhere_for_class_method",
      "completion_of_already_indexed_classes_methods", "running_builds_tests"
    )

    private const val FORTH_QUESTION_NONE_OPTION = "nothing"

    internal val FORTH_QUESTION_OPTIONS_PLUS_NONE = FORTH_QUESTION_OPTIONS + FORTH_QUESTION_NONE_OPTION
  }

  /** Increase the additional number when feedback format is changed */
  override val myFeedbackJsonVersion: Int = super.myFeedbackJsonVersion + 2

  override val zendeskTicketTitle: String = "${ApplicationNamesInfo.getInstance().fullProductName} Startup Feedback"
  override val zendeskFeedbackType: String = "Startup Feedback"

  override val myFeedbackReportId: String = "startup_feedback"
  override val myTitle: String = IdeStartupFeedbackMessagesBundle.message("ide.startup.dialog.top.title")

  private val myOptionCountInColumn = 4

  private val myFirstQuestionJsonElementName = "feelings_while_waiting_ide_to_load"
  private val mySecondQuestionJsonElementName = "rate_waiting_time"
  private val myThirdQuestionJsonElementName = "describes_you_the_best"
  private val myForthQuestionJsonElementName = "desirable_functionality_while_ide_not_ready"


  override val myBlocks: List<FeedbackBlock> = listOf(
    TopLabelBlock(
      IdeStartupFeedbackMessagesBundle.message("ide.startup.dialog.title", ApplicationNamesInfo.getInstance().fullProductName)
    ).setBottomGap(BottomGap.MEDIUM),
    ComboBoxBlock(IdeStartupFeedbackMessagesBundle.message("ide.startup.dialog.question.1.label"),
                  FIRST_QUESTION_OPTIONS,
                  myFirstQuestionJsonElementName).randomizeOptionOrder(),
    ComboBoxBlock(IdeStartupFeedbackMessagesBundle.message("ide.startup.dialog.question.2.label"),
                  SECOND_QUESTION_OPTIONS,
                  mySecondQuestionJsonElementName),
    ComboBoxBlock(IdeStartupFeedbackMessagesBundle.message("ide.startup.dialog.question.3.label"),
                  THIRD_QUESTION_OPTIONS,
                  myThirdQuestionJsonElementName).setColumnSize(52),
    CustomCheckBoxGroupBlock(
      IdeStartupFeedbackMessagesBundle.message("ide.startup.dialog.question.4.label"),
      List(myOptionCountInColumn) {
        CheckBoxItemData(
          IdeStartupFeedbackMessagesBundle.message("ide.startup.dialog.question.4.option.${(it + 1)}"), FORTH_QUESTION_OPTIONS[it]
        ) to CheckBoxItemData(
          IdeStartupFeedbackMessagesBundle.message("ide.startup.dialog.question.4.option.${myOptionCountInColumn + (it + 1)}"),
          FORTH_QUESTION_OPTIONS[myOptionCountInColumn + it]
        )
      },
      CheckBoxItemData(IdeStartupFeedbackMessagesBundle.message("ide.startup.dialog.question.4.option.none"), FORTH_QUESTION_NONE_OPTION),
      myForthQuestionJsonElementName
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

  override fun sendFeedbackData() {
    if (emailBlockWithAgreement.getEmailAddressIfSpecified().isEmpty()) {
      val collectedData = collectDataToJsonObject()
      logFeedbackFirstQuestionAnswer(collectedData[myFirstQuestionJsonElementName]!!.jsonPrimitive.content)
      logFeedbackSecondQuestionAnswer(collectedData[mySecondQuestionJsonElementName]!!.jsonPrimitive.content)

      val thirdQuestionAnswer = collectedData[myThirdQuestionJsonElementName]!!.jsonPrimitive.content
      logFeedbackThirdQuestionAnswer(COLLECTOR_THIRD_QUESTION_OPTIONS[THIRD_QUESTION_OPTIONS.indexOf(thirdQuestionAnswer)])

      val forthQuestionData = collectedData[myForthQuestionJsonElementName]!!.jsonObject
      val checkedForthQuestionOptions = FORTH_QUESTION_OPTIONS_PLUS_NONE.filter {
        forthQuestionData[it]!!.jsonPrimitive.boolean
      }.toList()
      logFeedbackForthQuestionAnswer(checkedForthQuestionOptions)
    }

    super.sendFeedbackData()
  }
}