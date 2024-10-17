// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.actions

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.codeInsight.hint.HintManagerImpl.ActionToIgnore
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.lang.documentation.ide.impl.DocumentationManager
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.editor.EditorMouseHoverPopupManager
import com.intellij.openapi.project.DumbAware
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class ShowHoverInfoAction: AnAction(), ActionToIgnore, PopupAction, DumbAware, PerformWithDocumentsCommitted {
  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val editor = CommonDataKeys.EDITOR.getData(e.dataContext)
    if (editor == null) {
      e.presentation.isEnabledAndVisible = false
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val editor = CommonDataKeys.EDITOR.getData(e.dataContext) ?: return

    (project as ComponentManagerEx).getCoroutineScope().launch {
      // When there are multiple warnings at the same offset, this will return the HighlightInfo
      // containing all of them, not just the first one as found by findInfo()
      val highlightInfo = readAction {
        (DaemonCodeAnalyzer.getInstance(project) as DaemonCodeAnalyzerImpl)
          .findHighlightsByOffset(editor.document, editor.caretModel.offset, false, false, HighlightSeverity.INFORMATION)
      }
      withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
        if (highlightInfo != null) {
          EditorMouseHoverPopupManager.getInstance().showInfoTooltip(editor, highlightInfo, editor.caretModel.offset, false, true, true, true)
        }
        else {
          // No errors, just show doc
          DocumentationManager.getInstance(project).actionPerformed(e.dataContext)
        }
      }
    }}
}