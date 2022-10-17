// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback.kotlinRejecters

import com.intellij.feedback.common.OpenApplicationFeedbackShower.Companion.showNotification
import com.intellij.feedback.kotlinRejecters.bundle.KotlinRejectersFeedbackBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

private class TestShowKotlinRejectersFeedbackDialogAction : AnAction(KotlinRejectersFeedbackBundle.message("test.action.name")) {
  override fun actionPerformed(e: AnActionEvent) {
    showNotification(e.project, true)
  }
  
  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}