// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback

import com.intellij.openapi.project.Project
import com.intellij.platform.feedback.dialog.BlockBasedFeedbackDialog
import com.intellij.platform.feedback.dialog.SystemDataJsonSerializable

/**
 * Represents the config for In-IDE feedback survey.
 *
 * @see com.intellij.platform.feedback.NotificationBasedFeedbackSurveyConfig
 */
interface InIdeFeedbackSurveyConfig : NotificationBasedFeedbackSurveyConfig {

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
