// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.documentation.ide.actions

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.documentation.DocFontSizePopup
import com.intellij.codeInsight.documentation.DocumentationEditorPane
import com.intellij.codeInsight.hint.HintManagerImpl.ActionToIgnore
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.project.DumbAware

internal class AdjustFontSizeAction : AnAction(CodeInsightBundle.message("javadoc.adjust.font.size")), ActionToIgnore, DumbAware {

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.dataContext.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT) is DocumentationEditorPane
  }

  override fun actionPerformed(e: AnActionEvent) {
    val editorPane = e.dataContext.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT) as DocumentationEditorPane
    DocFontSizePopup.show(editorPane, editorPane::applyFontProps)
  }
}
