// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.extractMethod.newImpl.inplace

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.refactoring.extractMethod.newImpl.ExtractMethodHelper

class EditorState(val editor: Editor){
  private val caretToRevert: Int = editor.caretModel.currentCaret.offset
  private val selectionToRevert: TextRange? = ExtractMethodHelper.findEditorSelection(editor)
  private val textToRevert: String = editor.document.text

  fun revert() {
    val project = editor.project
    ApplicationManager.getApplication().runWriteAction {
      editor.document.setText(textToRevert)
      if (project != null) {
        PsiDocumentManager.getInstance(project).commitDocument(editor.document)
      }
    }
    editor.caretModel.moveToOffset(caretToRevert)
    if (selectionToRevert != null) {
      editor.selectionModel.setSelection(selectionToRevert.startOffset, selectionToRevert.endOffset)
    }
  }
}