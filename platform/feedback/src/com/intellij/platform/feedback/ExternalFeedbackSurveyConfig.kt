// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback

import com.intellij.openapi.project.Project

interface ExternalFeedbackSurveyConfig : FeedbackSurveyConfig {

  fun getUrlToSurvey(project: Project): String

  fun updateStateAfterRespondActionInvoked(project: Project)
}