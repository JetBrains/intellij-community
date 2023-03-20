// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints.settings.language

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.hint.EditorFragmentComponent
import com.intellij.lang.Language
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiDocumentManager
import com.intellij.ui.EditorTextField
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import javax.swing.border.LineBorder

internal val SETTINGS_EDITOR_MARKER: Key<Boolean> = Key.create("inlay.settings.editor")

fun isInlaySettingsEditor(editor: Editor) : Boolean {
  return editor.getUserData(SETTINGS_EDITOR_MARKER) == true
}

fun createEditor(language: Language,
                 project: Project,
                 updateHints: (editor: Editor) -> Any): EditorTextField {
  val fileType: FileType = language.associatedFileType ?: FileTypes.PLAIN_TEXT
  val editorField = object : EditorTextField(null, project, fileType, true, false) {
    override fun createEditor(): EditorEx {
      val editor = super.createEditor()
      editor.putUserData(SETTINGS_EDITOR_MARKER, true)
      updateHints(editor)
      return editor
    }
  }
  editorField.font = EditorFontType.PLAIN.globalFont
  editorField.border = LineBorder(JBColor.border())
  editorField.addSettingsProvider { editor ->
    editor.setVerticalScrollbarVisible(true)
    editor.setHorizontalScrollbarVisible(true)
    with(editor.settings) {
      additionalLinesCount = 0
      isAutoCodeFoldingEnabled = false
    }
    // Sadly, but we can't use daemon here, because we need specific kind of settings instance here.
    editor.document.addDocumentListener(object : DocumentListener {
      override fun documentChanged(event: DocumentEvent) {
        updateHints(editor)
      }
    })
    editor.backgroundColor = EditorFragmentComponent.getBackgroundColor(editor, false)
    editor.setBorder(JBUI.Borders.empty())
    // If editor is created as not viewer, daemon is enabled automatically. But we want to collect hints manually with another settings.
    val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
    if (psiFile != null) {
      DaemonCodeAnalyzer.getInstance(project).setHighlightingEnabled(psiFile, false)
    }
  }
  ReadAction.run<Throwable> {  editorField.setCaretPosition(0) }
  return editorField
}
