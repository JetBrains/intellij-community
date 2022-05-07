// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback.disabledKotlinPlugin

import com.intellij.feedback.common.OpenedApplicationFeedbackShower.Companion.showNotification
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class TestShowDisabledKotlinPluginFeedbackDialogAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    showNotification(e.project, true)
  }
}