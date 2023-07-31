// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback

import com.intellij.openapi.project.Project
import com.intellij.platform.feedback.impl.dialog.BlockBasedFeedbackDialog
import com.intellij.platform.feedback.impl.dialog.SystemDataJsonSerializable

interface InIdeFeedbackSurveyConfig : FeedbackSurveyConfig {

  fun updateStateAfterDialogClosedOk(project: Project)

  fun createFeedbackDialog(project: Project, forTest: Boolean): BlockBasedFeedbackDialog<out SystemDataJsonSerializable>
}