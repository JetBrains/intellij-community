// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.rotateSelection

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler

class RotateSelectionsHandler(private val isBackwards: Boolean) : EditorWriteActionHandler() {
  override fun executeWriteAction(editor: Editor, caret: Caret?, dataContext: DataContext?) {
    if (editor.caretModel.caretCount <= 1) return

    val carets = editor.caretModel.allCarets
      .sortedByDescending { it.selectionRange.endOffset }

    val textToReplace = carets
      .map { editor.document.getText(it.selectionRange) }
      .rotateElements(!isBackwards) // isBackwards is inversed because carets are in the reverse order (sorted by descending offset)

    assert(carets.size == textToReplace.size)
    for ((i, caret) in carets.withIndex()) {
      val selection = caret.selectionRange
      val newText = textToReplace[i]
      editor.document.replaceString(selection.startOffset, selection.endOffset, newText)
      caret.setSelection(selection.startOffset, selection.startOffset + newText.length)
      caret.moveToOffset(caret.selectionEnd)
    }
  }

  private fun <T> List<T>.rotateElements(isBackwards: Boolean): List<T> {
    if (this.size <= 1) return this.toList()
    return if (isBackwards) {
      this.subList(1, this.size) + this[0]
    } else {
      listOf(this[this.size - 1]) + this.subList(0, this.size - 1)
    }
  }
}