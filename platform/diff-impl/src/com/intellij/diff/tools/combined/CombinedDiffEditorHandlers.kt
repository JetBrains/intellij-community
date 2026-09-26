// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.combined

import com.intellij.idea.AppMode
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.EditorImpl
import org.jetbrains.annotations.ApiStatus

internal abstract class CombinedDiffBaseEditorForEachCaretHandler(private val original: EditorActionHandler) : EditorActionHandler.ForEachCaret() {
  final override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext?): Boolean =
    original.isEnabled(editor, caret, dataContext)

  final override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext?) {
    val combined = editor.diffViewer
    if (!AppMode.isRemoteDevHost() && combined != null && caret != null) {
      doExecute(combined, editor, caret, dataContext)
      return
    }
    original.execute(editor, caret, dataContext)
  }

  abstract fun doExecute(combined: CombinedDiffViewer, editor: Editor, caret: Caret, dc: DataContext?)
}

@ApiStatus.Internal
abstract class CombinedDiffBaseEditorWithSelectionHandler(private val original: EditorActionHandler) : EditorActionHandler() {
  final override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext?): Boolean =
    original.isEnabled(editor, caret, dataContext)

  final override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext?) {
    val combined = editor.diffViewer
    if (!AppMode.isRemoteDevHost() && combined != null) {
      doExecute(combined, editor, caret, dataContext)
      return
    }
    original.execute(editor, caret, dataContext)
  }

  abstract fun doExecute(combined: CombinedDiffViewer, editor: Editor, caret: Caret?, dc: DataContext?)
}


internal class CombinedDiffEditorUpHandler(private val original: EditorActionHandler) : CombinedDiffBaseEditorForEachCaretHandler(original) {
  override fun doExecute(combined: CombinedDiffViewer, editor: Editor, caret: Caret, dc: DataContext?) {
    if (caret.isOnFirstVisibleLine() && combined.canGoPrevBlock()) {
      combined.moveCaretToPrevBlock()
      return
    }
    original.execute(editor, caret, dc)
    combined.scrollToCaret()
  }
}

internal class CombinedDiffEditorUpWithSelectionHandler(private val original: EditorActionHandler) : CombinedDiffBaseEditorWithSelectionHandler(original) {
  override fun doExecute(combined: CombinedDiffViewer, editor: Editor, caret: Caret?, dc: DataContext?) {
    val currentCaret = editor.caretModel.currentCaret
    if (currentCaret.isOnFirstVisibleLine() && currentCaret.isOnFirstVisibleColumn() && combined.canGoPrevBlock()) {
      combined.moveCaretToPrevBlock()
      return
    }
    original.execute(editor, caret, dc)
    combined.scrollToCaret()
  }
}

internal class CombinedDiffEditorDownHandler(private val original: EditorActionHandler) : CombinedDiffBaseEditorForEachCaretHandler(original) {
  override fun doExecute(combined: CombinedDiffViewer, editor: Editor, caret: Caret, dc: DataContext?) {
    if (caret.isOnLastVisibleLine() && combined.canGoNextBlock()) {
      combined.moveCaretToNextBlock()
      return
    }
    original.execute(editor, caret, dc)
    combined.scrollToCaret()
  }
}

internal class CombinedDiffEditorDownWithSelectionHandler(private val original: EditorActionHandler) : CombinedDiffBaseEditorWithSelectionHandler(original) {
  override fun doExecute(combined: CombinedDiffViewer, editor: Editor, caret: Caret?, dc: DataContext?) {
    val currentCaret = editor.caretModel.currentCaret
    if (currentCaret.isOnLastVisibleLine() && currentCaret.isOnLastVisibleColumn(editor) && combined.canGoNextBlock()) {
      combined.moveCaretToNextBlock()
      return
    }
    original.execute(editor, caret, dc)
    combined.scrollToCaret()
  }
}

internal class CombinedDiffEditorLeftHandler(private val original: EditorActionHandler) : CombinedDiffBaseEditorForEachCaretHandler(original) {
  override fun doExecute(combined: CombinedDiffViewer, editor: Editor, caret: Caret, dc: DataContext?) {
    if (caret.isOnFirstVisibleLine() && caret.isOnFirstVisibleColumn() && combined.canGoPrevBlock()) {
      combined.moveCaretToPrevBlock()
      return
    }
    original.execute(editor, caret, dc)
    combined.scrollToCaret()
  }

}

internal class CombinedDiffEditorRightHandler(private val original: EditorActionHandler) : CombinedDiffBaseEditorForEachCaretHandler(original) {
  override fun doExecute(combined: CombinedDiffViewer, editor: Editor, caret: Caret, dc: DataContext?) {
    val isOnLastPosition = caret.isOnLastVisibleColumn(editor)
    if (caret.isOnLastVisibleLine() && combined.canGoNextBlock() && isOnLastPosition) {
      combined.moveCaretToNextBlock()
      return
    }
    original.execute(editor, caret, dc)
    combined.scrollToCaret()
  }
}

private val Editor.diffViewer: CombinedDiffViewer?
  get() = this.getUserData(COMBINED_DIFF_VIEWER_KEY)

private fun Caret.isOnFirstVisibleLine(): Boolean = visualPosition.line == 0

private fun Caret.isOnFirstVisibleColumn() = visualPosition.column == 0

private fun Caret.isOnLastVisibleLine(): Boolean {
  val editorImpl = editor as? EditorImpl ?: return false
  return visualPosition.line == editorImpl.visibleLineCount - 1
}

private fun Caret.isOnLastVisibleColumn(editor: Editor) =
  EditorUtil.getLastVisualLineColumnNumber(editor, visualPosition.line) == visualPosition.column

internal class CombinedDiffEditorPageUpHandler(original: EditorActionHandler) : CombinedDiffBaseEditorForEachCaretHandler(original) {
  override fun doExecute(combined: CombinedDiffViewer, editor: Editor, caret: Caret, dc: DataContext?) {
    combined.moveCaretPageUp()
  }
}

internal class CombinedDiffEditorPageDownHandler(original: EditorActionHandler) : CombinedDiffBaseEditorForEachCaretHandler(original) {
  override fun doExecute(combined: CombinedDiffViewer, editor: Editor, caret: Caret, dc: DataContext?) {
    combined.moveCaretPageDown()
  }
}