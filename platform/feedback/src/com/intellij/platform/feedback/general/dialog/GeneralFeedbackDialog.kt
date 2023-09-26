// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.general.dialog


import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.project.Project
import com.intellij.platform.feedback.general.bundle.GeneralFeedbackBundle
import com.intellij.platform.feedback.general.statistics.GeneralFeedbackCountCollector

class GeneralFeedbackDialog(project: Project?,
                            forTest: Boolean
) : BaseGeneralFeedbackDialog(null, project, forTest) {

  /** Increase the additional number when feedback format is changed */
  override val myFeedbackJsonVersion: Int = super.myFeedbackJsonVersion + 1

  override val zendeskTicketTitle: String = "${ApplicationNamesInfo.getInstance().productName} Feedback"
  override val zendeskFeedbackType: String = "General Feedback"
  override val myFeedbackReportId: String = "general_feedback"

  override val myTitle: String = GeneralFeedbackBundle.message("general.dialog.top.title")

  init {
    init()
    GeneralFeedbackCountCollector.logGeneralFeedbackDialogShown()
  }

  override fun doCancelAction() {
    super.doCancelAction()
    GeneralFeedbackCountCollector.logGeneralFeedbackDialogCanceled()
  }

  override fun doOKAction() {
    super.doOKAction()

    val collectedData = collectDataToJsonObject()
    GeneralFeedbackCountCollector.logGeneralFeedbackSent(
      collectedData[interfaceJsonElementName].toString().toInt(),
      collectedData[priceJsonElementName].toString().toInt(),
      collectedData[stabilityJsonElementName].toString().toInt(),
      collectedData[featureSetJsonElementName].toString().toInt(),
      collectedData[performanceJsonElementName].toString().toInt()
    )
  }
}