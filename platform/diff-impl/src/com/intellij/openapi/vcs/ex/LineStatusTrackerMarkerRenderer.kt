// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.ex

import com.intellij.diff.DiffApplicationSettings
import com.intellij.diff.comparison.ByWord
import com.intellij.diff.comparison.ComparisonPolicy
import com.intellij.diff.util.DiffUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.MarkupEditorFilter
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.ui.EditorTextField
import java.awt.Point
import javax.swing.JComponent

abstract class LineStatusTrackerMarkerRenderer(
  tracker: LineStatusTrackerI<*>,
  editorFilter: MarkupEditorFilter? = null
) : LineStatusMarkerRendererWithPopup(tracker.project, tracker.document, tracker, tracker.disposable, editorFilter),
    LineStatusMarkerRendererWithPopupController {

  protected val vcsDocument: Document = tracker.vcsDocument
  protected val fileType: FileType? = tracker.virtualFile?.fileType

  final override fun createPopupPanel(editor: Editor,
                                      range: Range,
                                      mousePosition: Point?,
                                      popupDisposable: Disposable): LineStatusMarkerPopupPanel {
    var editorComponent: JComponent? = null
    if (range.hasVcsLines()) {
      editorComponent = createVcsContentComponent(range, editor, popupDisposable)
    }
    val actions = createToolbarActions(editor, range, mousePosition) +
                  createAdditionalToolbarActions(editor, range, mousePosition, popupDisposable)
    val toolbar = LineStatusMarkerPopupPanel.buildToolbar(editor, actions, popupDisposable)
    val additionalInfoPanel = createAdditionalInfoPanel(editor, range, mousePosition, popupDisposable)
    return LineStatusMarkerPopupPanel.create(editor, toolbar, editorComponent, additionalInfoPanel)
  }

  private fun createVcsContentComponent(range: Range, editor: Editor, disposable: Disposable): JComponent {
    val vcsRange = DiffUtil.getLinesRange(vcsDocument, range.vcsLine1, range.vcsLine2)
    val vcsContent = DiffUtil.getLinesContent(vcsDocument, range.vcsLine1, range.vcsLine2).toString()
    val textField = LineStatusMarkerPopupPanel.createTextField(editor, vcsContent)
    LineStatusMarkerPopupPanel.installBaseEditorSyntaxHighlighters(project, textField, vcsDocument, vcsRange,
                                                                   fileType ?: PlainTextFileType.INSTANCE)
    installWordDiff(editor, textField, range, disposable)
    return LineStatusMarkerPopupPanel.createEditorComponent(editor, textField)
  }

  protected open fun createToolbarActions(editor: Editor, range: Range, mousePosition: Point?): List<AnAction> = emptyList()
  protected open fun createAdditionalToolbarActions(editor: Editor, range: Range, mousePosition: Point?, popupDisposable: Disposable): List<AnAction> = emptyList()

  protected open fun createAdditionalInfoPanel(editor: Editor,
                                               range: Range,
                                               mousePosition: Point?,
                                               disposable: Disposable): JComponent? = null

  private fun installWordDiff(editor: Editor,
                              textField: EditorTextField,
                              range: Range,
                              disposable: Disposable) {
    if (!DiffApplicationSettings.getInstance().SHOW_LST_WORD_DIFFERENCES) return
    if (!range.hasLines() || !range.hasVcsLines()) return

    val vcsContent = DiffUtil.getLinesContent(vcsDocument, range.vcsLine1, range.vcsLine2)
    val currentContent = DiffUtil.getLinesContent(document, range.line1, range.line2)
    val wordDiff = BackgroundTaskUtil.tryComputeFast(
      { indicator -> ByWord.compare(vcsContent, currentContent, ComparisonPolicy.DEFAULT, indicator) }, 200)
                   ?: return
    LineStatusMarkerPopupPanel.installMasterEditorWordHighlighters(editor, range.line1, range.line2, wordDiff, disposable)
    LineStatusMarkerPopupPanel.installPopupEditorWordHighlighters(textField, wordDiff)
  }
}