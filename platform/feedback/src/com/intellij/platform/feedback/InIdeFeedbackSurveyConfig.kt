// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback

import com.intellij.openapi.project.Project
import com.intellij.platform.feedback.dialog.BlockBasedFeedbackDialog
import com.intellij.platform.feedback.dialog.SystemDataJsonSerializable
import com.intellij.platform.feedback.impl.updateCommonFeedbackSurveysStateAfterSent
import com.intellij.util.concurrency.annotations.RequiresEdt

/**
 * Represents the config for In-IDE feedback survey.
 *
 * @see com.intellij.platform.feedback.FeedbackSurveyConfig
 */
interface InIdeFeedbackSurveyConfig : FeedbackSurveyConfig {

  /**
   * Returns a dialog with the feedback survey.
   *
   * @see BlockBasedFeedbackDialog
   */
  fun createFeedbackDialog(project: Project, forTest: Boolean): BlockBasedFeedbackDialog<out SystemDataJsonSerializable>

  /**
   * Performs additional custom state updates after the feedback dialog is closed by the ok button.
   */
  fun updateStateAfterDialogClosedOk(project: Project)
}

/**
 * Shows the feedback dialog for the user to submit feedback.
 *
 * Normally the dialog is shown when the user clicks a survey notification.
 * This function is intended for feature-specific feedback actions,
 * where [com.intellij.platform.feedback.FeedbackSurveyConfig.isSuitableToShowByExplicitUserAction]
 * is used for [com.intellij.openapi.actionSystem.AnAction.update]
 * and this function is used for [com.intellij.openapi.actionSystem.AnAction.actionPerformed].
 */
@RequiresEdt
fun InIdeFeedbackSurveyConfig.showFeedbackDialog(project: Project, forTest: Boolean) {
  val dialog = createFeedbackDialog(project, forTest)
  val isOk = dialog.showAndGet()
  if (isOk && !forTest) {
    updateStateAfterDialogClosedOk(project)
    updateCommonFeedbackSurveysStateAfterSent(this)
  }
}
