// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback.evaluation.dialog

import com.intellij.feedback.common.dialog.BlockBasedFeedbackDialogWithEmail
import com.intellij.feedback.common.dialog.CommonFeedbackSystemInfoData
import com.intellij.feedback.common.dialog.showFeedbackSystemInfoDialog
import com.intellij.feedback.common.dialog.uiBlocks.*
import com.intellij.feedback.common.notification.ThanksForFeedbackNotification
import com.intellij.feedback.evaluation.bundle.EvaluationFeedbackBundle
import com.intellij.feedback.evaluation.statistics.EvaluationFeedbackCountCollector.Companion.logEvaluationFeedbackSent
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.project.Project
import javax.swing.Action

class EvaluationFeedbackDialog(project: Project?,
                               forTest: Boolean
) : BlockBasedFeedbackDialogWithEmail<CommonFeedbackSystemInfoData>(project, forTest) {

  /** Increase the additional number when feedback format is changed */
  override val myFeedbackJsonVersion: Int = super.myFeedbackJsonVersion + 1

  override val zendeskTicketTitle: String = "${ApplicationNamesInfo.getInstance().productName} Evaluation Feedback"
  override val zendeskFeedbackType: String = "Evaluation Feedback"
  override val myFeedbackReportId: String = "evaluation_feedback"

  override val myTitle: String = EvaluationFeedbackBundle.message("evaluation.dialog.top.title")

  private val interfaceJsonElementName = "interface"
  private val priceJsonElementName = "price"
  private val stabilityJsonElementName = "stability"
  private val featureSetJsonElementName = "feature_set"
  private val performanceJsonElementName = "performance"

  private val ratingItems: List<RatingItem> = listOf(
    RatingItem(EvaluationFeedbackBundle.message("evaluation.dialog.rating.block.label.1"), interfaceJsonElementName),
    RatingItem(EvaluationFeedbackBundle.message("evaluation.dialog.rating.block.label.2"), priceJsonElementName),
    RatingItem(EvaluationFeedbackBundle.message("evaluation.dialog.rating.block.label.3"), stabilityJsonElementName),
    RatingItem(EvaluationFeedbackBundle.message("evaluation.dialog.rating.block.label.4"), featureSetJsonElementName),
    RatingItem(EvaluationFeedbackBundle.message("evaluation.dialog.rating.block.label.5"), performanceJsonElementName)
  )

  override val myBlocks: List<FeedbackBlock> = listOf(
    TopLabelBlock(EvaluationFeedbackBundle.message("evaluation.dialog.title")),
    DescriptionBlock(EvaluationFeedbackBundle.message("evaluation.dialog.description")),
    RatingGroupBlock(EvaluationFeedbackBundle.message("evaluation.dialog.rating.block.top.label"), ratingItems)
      .setHint(EvaluationFeedbackBundle.message("evaluation.dialog.rating.block.hint")).setRandomOrder(true),
    TextAreaBlock(EvaluationFeedbackBundle.message("evaluation.dialog.text.area.details"), "tell_us_more")
  )

  override val mySystemInfoData: CommonFeedbackSystemInfoData by lazy {
    CommonFeedbackSystemInfoData.getCurrentData()
  }
  override val myShowFeedbackSystemInfoDialog: () -> Unit = {
    showFeedbackSystemInfoDialog(myProject, mySystemInfoData)
  }

  init {
    init()
  }

  override fun showThanksNotification() {
    ThanksForFeedbackNotification(description = EvaluationFeedbackBundle.message(
      "evaluation.notification.thanks.feedback.content", ApplicationNamesInfo.getInstance().fullProductName)).notify(myProject)
  }


  override fun getCancelAction(): Action {
    val cancelAction = super.getCancelAction()
    cancelAction.putValue(Action.NAME, EvaluationFeedbackBundle.message("evaluation.dialog.cancel.label"))
    return cancelAction
  }

  override fun doOKAction() {
    super.doOKAction()

    val collectedData = collectDataToJsonObject()
    logEvaluationFeedbackSent(
      collectedData[interfaceJsonElementName].toString().toInt(),
      collectedData[priceJsonElementName].toString().toInt(),
      collectedData[stabilityJsonElementName].toString().toInt(),
      collectedData[featureSetJsonElementName].toString().toInt(),
      collectedData[performanceJsonElementName].toString().toInt()
    )
  }

  override fun sendFeedbackData() {
    super.sendFeedbackData()

    //  TODO: Send to old Zendesk
  }
}