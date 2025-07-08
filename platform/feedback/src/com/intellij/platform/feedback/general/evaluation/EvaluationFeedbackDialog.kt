// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.general.evaluation


import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.project.Project
import com.intellij.platform.feedback.dialog.CommonBlockBasedFeedbackDialogWithEmail
import com.intellij.platform.feedback.dialog.uiBlocks.*
import com.intellij.platform.feedback.general.evaluation.EvaluationFeedbackCountCollector.logEvaluationFeedbackDialogCanceled
import com.intellij.platform.feedback.general.evaluation.EvaluationFeedbackCountCollector.logEvaluationFeedbackDialogShown
import com.intellij.platform.feedback.general.evaluation.EvaluationFeedbackCountCollector.logEvaluationFeedbackSent
import com.intellij.platform.feedback.impl.notification.ThanksForFeedbackNotification
import kotlinx.serialization.json.jsonPrimitive
import javax.swing.Action

internal class EvaluationFeedbackDialog(
  project: Project?, forTest: Boolean
) : CommonBlockBasedFeedbackDialogWithEmail(project, forTest) {

  /** Increase the additional number when feedback format is changed */
  override val myFeedbackJsonVersion: Int = super.myFeedbackJsonVersion + 3

  override val zendeskTicketTitle: String = "${ApplicationNamesInfo.getInstance().productName} Evaluation Feedback"
  override val zendeskFeedbackType: String = "Evaluation Feedback"
  override val myFeedbackReportId: String = "evaluation_feedback"

  override val myTitle: String = EvaluationFeedbackBundle.message("evaluation.dialog.top.title")

  private val interfaceJsonElementName = "interface"
  private val priceJsonElementName = "price"
  private val stabilityJsonElementName = "stability"
  private val featureSetJsonElementName = "feature_set"
  private val performanceJsonElementName = "performance"
  private val tellUsMoreJsonElementName = "tell_us_more"

  private val ratingItems: List<RatingItem> = listOf(
    RatingItem(EvaluationFeedbackBundle.message("evaluation.dialog.rating.block.label.1"), interfaceJsonElementName),
    RatingItem(EvaluationFeedbackBundle.message("evaluation.dialog.rating.block.label.2"), priceJsonElementName),
    RatingItem(EvaluationFeedbackBundle.message("evaluation.dialog.rating.block.label.3"), stabilityJsonElementName),
    RatingItem(EvaluationFeedbackBundle.message("evaluation.dialog.rating.block.label.4"), featureSetJsonElementName),
    RatingItem(EvaluationFeedbackBundle.message("evaluation.dialog.rating.block.label.5"), performanceJsonElementName)
  )

  override val myBlocks: List<FeedbackBlock> = mutableListOf<FeedbackBlock>().apply {
    add(TopLabelBlock(EvaluationFeedbackBundle.message("evaluation.dialog.title")))
    add(DescriptionBlock(EvaluationFeedbackBundle.message("evaluation.dialog.description")))
    add(RatingGroupBlock(EvaluationFeedbackBundle.message("evaluation.dialog.rating.block.top.label"), ratingItems)
          .setHint(EvaluationFeedbackBundle.message("evaluation.dialog.rating.block.hint")).setRandomOrder(true))
    add(TextAreaBlock(EvaluationFeedbackBundle.message("evaluation.dialog.text.area.details"), tellUsMoreJsonElementName))
  }

  init {
    init()
    logEvaluationFeedbackDialogShown()
  }

  override fun doCancelAction() {
    super.doCancelAction()
    logEvaluationFeedbackDialogCanceled()
  }

  override fun doOKAction() {
    super.doOKAction()

    if (emailBlockWithAgreement.getEmailAddressIfSpecified().isBlank()) {
      val collectedData = collectDataToJsonObject()
      logEvaluationFeedbackSent(
        collectedData[interfaceJsonElementName].toString().toInt(),
        collectedData[priceJsonElementName].toString().toInt(),
        collectedData[stabilityJsonElementName].toString().toInt(),
        collectedData[featureSetJsonElementName].toString().toInt(),
        collectedData[performanceJsonElementName].toString().toInt()
      )
    }
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

  override fun shouldAutoCloseZendeskTicket(): Boolean {
    val collectedData = collectDataToJsonObject()
    return collectedData[tellUsMoreJsonElementName]?.jsonPrimitive?.content?.isBlank() ?: return true
  }
}