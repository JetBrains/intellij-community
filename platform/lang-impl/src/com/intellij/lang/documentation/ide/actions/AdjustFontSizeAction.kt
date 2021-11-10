// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.documentation.ide.actions

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.documentation.DocFontSizePopup
import com.intellij.codeInsight.documentation.DocumentationEditorPane
import com.intellij.codeInsight.hint.HintManagerImpl.ActionToIgnore
import com.intellij.lang.documentation.ide.impl.DocumentationToolWindowManager
import com.intellij.lang.documentation.ide.ui.toolWindowUI
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.wm.impl.content.BaseLabel

internal class AdjustFontSizeAction : AnAction(CodeInsightBundle.message("javadoc.adjust.font.size")), ActionToIgnore, DumbAware {

  private fun editorPane(dc: DataContext): DocumentationEditorPane? {
    val component = dc.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT)
    if (component is DocumentationEditorPane) {
      return component
    }
    val toolWindow = dc.getData(PlatformDataKeys.TOOL_WINDOW)
                     ?: return null
    if (toolWindow.id != DocumentationToolWindowManager.TOOL_WINDOW_ID) {
      return null
    }
    val content = if (component is BaseLabel) {
      component.content
    }
    else {
      toolWindow.contentManager.selectedContent
    }
    return content?.toolWindowUI?.editorPane
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = editorPane(e.dataContext) != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val editorPane = requireNotNull(editorPane(e.dataContext))
    DocFontSizePopup.show(editorPane, editorPane::applyFontProps)
  }
}
