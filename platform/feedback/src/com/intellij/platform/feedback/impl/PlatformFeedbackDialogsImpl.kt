// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.impl

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.platform.feedback.general.evaluation.EvaluationFeedbackDialog
import com.intellij.platform.feedback.general.general.GeneralFeedbackDialog
import com.intellij.platform.feedback.pluginPage.DisablePluginPageFeedbackDialog
import com.intellij.platform.feedback.pluginPage.UninstallPluginPageFeedbackDialog
import com.intellij.platform.ide.impl.feedback.PlatformFeedbackDialogs

class PlatformFeedbackDialogsImpl : PlatformFeedbackDialogs() {

  override fun createGeneralFeedbackDialog(project: Project?): DialogWrapper {
    return GeneralFeedbackDialog(project, false)
  }

  override fun createEvaluationFeedbackDialog(project: Project?): DialogWrapper {
    return EvaluationFeedbackDialog(project, false)
  }

  override fun getUninstallFeedbackDialog(pluginId: String, pluginName: String, project: Project?): DialogWrapper? {
    return UninstallPluginPageFeedbackDialog(pluginId, pluginName, project, false)
  }

  override fun getDisableFeedbackDialog(pluginId: String, pluginName: String, project: Project?): DialogWrapper? {
    return DisablePluginPageFeedbackDialog(pluginId, pluginName, project, false)
  }
}