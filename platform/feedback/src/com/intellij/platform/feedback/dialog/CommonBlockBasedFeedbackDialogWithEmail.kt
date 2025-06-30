// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.dialog

import com.intellij.openapi.project.Project

/**
 * The base class for building feedback dialogs with e-mail and only common system data.
 *
 * @see CommonBlockBasedFeedbackDialog
 */
abstract class CommonBlockBasedFeedbackDialogWithEmail(myProject: Project?, forTest: Boolean) : BlockBasedFeedbackDialogWithEmail<CommonFeedbackSystemData>(myProject, forTest) {
  override val mySystemInfoData: CommonFeedbackSystemData by lazy {
    CommonFeedbackSystemData.getCurrentData()
  }

  override val myShowFeedbackSystemInfoDialog: () -> Unit = {
    showFeedbackSystemInfoDialog(myProject, mySystemInfoData)
  }
}
