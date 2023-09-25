// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.evaluation.dialog


import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.project.Project
import com.intellij.platform.feedback.evaluation.bundle.EvaluationFeedbackBundle
import com.intellij.platform.feedback.evaluation.statistics.EvaluationFeedbackCountCollector.Companion.logEvaluationFeedbackDialogCanceled
import com.intellij.platform.feedback.evaluation.statistics.EvaluationFeedbackCountCollector.Companion.logEvaluationFeedbackDialogShown
import com.intellij.platform.feedback.evaluation.statistics.EvaluationFeedbackCountCollector.Companion.logEvaluationFeedbackSent
import com.intellij.platform.feedback.general.dialog.BaseGeneralFeedbackDialog

class EvaluationFeedbackDialog(project: Project?,
                               forTest: Boolean
) : BaseGeneralFeedbackDialog(
  EvaluationFeedbackBundle.message("evaluation.dialog.description"), project, forTest
) {

  /** Increase the additional number when feedback format is changed */
  override val myFeedbackJsonVersion: Int = super.myFeedbackJsonVersion + 1

  override val zendeskTicketTitle: String = "${ApplicationNamesInfo.getInstance().productName} Evaluation Feedback"
  override val zendeskFeedbackType: String = "Evaluation Feedback"
  override val myFeedbackReportId: String = "evaluation_feedback"

  override val myTitle: String = EvaluationFeedbackBundle.message("evaluation.dialog.top.title")

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