// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.pluginPage

import com.intellij.ide.feedback.PluginPageFeedbackDialogProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper

internal class PluginPageFeedbackDialogProviderImpl : PluginPageFeedbackDialogProvider() {
  override fun getUninstallFeedbackDialog(pluginId: String, pluginName: String, project: Project?): DialogWrapper {
    return UninstallPluginPageFeedbackDialog(pluginId, pluginName, project, false)
  }

  override fun getDisableFeedbackDialog(pluginId: String, pluginName: String, project: Project?): DialogWrapper {
    return DisablePluginPageFeedbackDialog(pluginId, pluginName, project, false)
  }
}