// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.documentation.ide.actions

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.hint.HintManagerImpl.ActionToIgnore
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.ui.showFontSizePopup

class AdjustFontSizeAction : AnAction(CodeInsightBundle.message("javadoc.adjust.font.size")), ActionToIgnore, DumbAware {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val documentationBrowser = documentationBrowser(e.dataContext)
    val ui = documentationBrowser?.ui
    e.presentation.isEnabledAndVisible = documentationBrowser != null &&
                                         ui?.editorPane?.isCustomSettingsEnabled == false
  }

  override fun actionPerformed(e: AnActionEvent) {
    val ui = requireNotNull(documentationBrowser(e.dataContext)).ui
    showFontSizePopup(ui.fontSize, ui.editorPane)
  }
}
