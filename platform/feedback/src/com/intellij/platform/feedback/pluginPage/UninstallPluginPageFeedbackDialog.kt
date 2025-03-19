// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.pluginPage

import com.intellij.openapi.project.Project

internal class UninstallPluginPageFeedbackDialog(pluginId: String, pluginName: String, project: Project?, forTest: Boolean) :
  PluginPageFeedbackDialog(pluginId, pluginName, CaseType.UNINSTALL, project, forTest) {

  override val myFeedbackReportId: String = "uninstall_plugin_page_feedback"
  override val zendeskFeedbackType: String = "uninstall_plugin_page_feedback"
  override val myFeedbackJsonVersion: Int = super.myFeedbackJsonVersion + 1

  init {
    init()
  }
}