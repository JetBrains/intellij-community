// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.analysis.problemsView.toolWindow

import com.intellij.codeInsight.daemon.impl.ShowIntentionsPass
import com.intellij.codeInsight.intention.impl.CachedIntentions
import com.intellij.codeInsight.intention.impl.IntentionActionWithTextCaching
import com.intellij.codeInsight.intention.impl.IntentionListStep
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys.PSI_FILE
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys.SELECTED_ITEM
import com.intellij.openapi.actionSystem.UpdateInBackground
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.application.ApplicationManager.getApplication
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.ui.UIUtil.isAncestor
import java.awt.event.MouseEvent

internal class ShowQuickFixesAction : AnAction(), UpdateInBackground {

  override fun update(event: AnActionEvent) {
    val node = event.getData(SELECTED_ITEM) as? ProblemNode
    val problem = node?.problem
    with(event.presentation) {
      isVisible = getApplication().isInternal || ProblemsView.getSelectedPanel(event.project) is HighlightingPanel
      isEnabled = isVisible && when (problem) {
        is HighlightingProblem -> isEnabled(event, problem)
        else -> false
      }
    }
  }

  override fun actionPerformed(event: AnActionEvent) {
    val node = event.getData(SELECTED_ITEM) as? ProblemNode
    when (val problem = node?.problem) {
      is HighlightingProblem -> actionPerformed(event, problem)
    }
  }


  private fun getEditor(psi: PsiFile, showEditor: Boolean): Editor? {
    val file = psi.virtualFile ?: return null
    val document = PsiDocumentManager.getInstance(psi.project).getDocument(psi) ?: return null
    val editors = EditorFactory.getInstance().editors(document, psi.project).filter { !it.isViewer }
    val editor = editors.findFirst().orElse(null) ?: return null
    if (!showEditor || editor.component.isShowing) return editor
    val manager = FileEditorManager.getInstance(psi.project) ?: return null
    if (manager.allEditors.none { isAncestor(it.component, editor.component) }) return null
    manager.openFile(file, false, true)
    return if (editor.component.isShowing) editor else null
  }

  private fun show(event: AnActionEvent, popup: JBPopup) {
    val mouse = event.inputEvent as? MouseEvent ?: return popup.showInBestPositionFor(event.dataContext)
    val point = mouse.locationOnScreen
    val panel = ProblemsView.getSelectedPanel(event.project)
    val button = mouse.source as? ActionButton
    if (panel != null && button != null) {
      point.translate(-mouse.x, -mouse.y)
      when (panel.isVertical) {
        true -> point.y += button.height
        else -> point.x += button.width
      }
    }
    popup.show(RelativePoint.fromScreen(point))
  }


  private fun isEnabled(event: AnActionEvent, problem: HighlightingProblem): Boolean {
    return getCachedIntentions(event, problem, false) != null
  }

  private fun actionPerformed(event: AnActionEvent, problem: HighlightingProblem) {
    val intentions = getCachedIntentions(event, problem, true) ?: return
    val editor: Editor = intentions.editor ?: return
    if (intentions.offset >= 0) editor.caretModel.moveToOffset(intentions.offset.coerceAtMost(editor.document.textLength))
    show(event, JBPopupFactory.getInstance().createListPopup(
      object : IntentionListStep(null, editor, intentions.file, intentions.file.project, intentions) {
        override fun chooseActionAndInvoke(cachedAction: IntentionActionWithTextCaching, file: PsiFile, project: Project, editor: Editor?) {
          editor?.contentComponent?.requestFocus()
          // hack until doWhenFocusSettlesDown will work as expected
          val modality = editor?.contentComponent?.let { ModalityState.stateForComponent(it) } ?: ModalityState.current()
          getApplication().invokeLater(
            {
              IdeFocusManager.getInstance(project).doWhenFocusSettlesDown {
                super.chooseActionAndInvoke(cachedAction, file, project, editor)
              }
            }, modality, project.disposed)
        }
      }
    ))
  }

  private fun getCachedIntentions(event: AnActionEvent, problem: HighlightingProblem, showEditor: Boolean): CachedIntentions? {
    val psi = event.getData(PSI_FILE) ?: return null
    val panel = ProblemsView.getSelectedPanel(event.project) ?: return null
    if (!panel.isShowing) return null
    val editor = panel.preview ?: getEditor(psi, showEditor) ?: return null
    val markers = problem.info?.quickFixActionMarkers ?: return null

    val info = ShowIntentionsPass.IntentionsInfo()
    markers.filter { it.second.isValid }.forEach { info.intentionsToShow.add(it.first) }
    info.offset = problem.info?.actualStartOffset ?: -1

    val intentions = CachedIntentions.createAndUpdateActions(psi.project, psi, editor, info)
    if (intentions.intentions.isNotEmpty()) return intentions
    return null // actions can be removed after updating
  }
}
