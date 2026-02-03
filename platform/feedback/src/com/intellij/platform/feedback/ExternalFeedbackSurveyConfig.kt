// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback

import com.intellij.openapi.project.Project

/**
 * Represents the config for external feedback survey.
 *
 * @see com.intellij.platform.feedback.NotificationBasedFeedbackSurveyConfig
 */
interface ExternalFeedbackSurveyConfig : NotificationBasedFeedbackSurveyConfig {

  /**
   * Returns a link to a feedback survey on the web.
   */
  fun getUrlToSurvey(project: Project): String

  /**
   * Performs additional custom state updates after a response action is invoked.
   */
  fun updateStateAfterRespondActionInvoked(project: Project)
}