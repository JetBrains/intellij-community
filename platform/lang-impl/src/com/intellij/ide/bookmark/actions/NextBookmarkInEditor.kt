// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.bookmark.actions

import com.intellij.ide.bookmark.BookmarkBundle.messagePointer
import com.intellij.ide.bookmark.BookmarkOccurrence
import com.intellij.ide.bookmark.BookmarksManager
import com.intellij.ide.bookmark.LineBookmark
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.*
import com.intellij.openapi.editor.actionSystem.EditorAction
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.fileEditor.FileDocumentManager

internal class NextBookmarkInEditorAction : EditorAction(NextBookmarkInEditor(true)) {
  init {
    templatePresentation.setText(messagePointer("bookmark.go.to.next.editor.action.text"))
  }
}

internal class PreviousBookmarkInEditorAction : EditorAction(NextBookmarkInEditor(false)) {
  init {
    templatePresentation.setText(messagePointer("bookmark.go.to.previous.editor.action.text"))
  }
}

private class NextBookmarkInEditor(val forward: Boolean) : EditorActionHandler() {
  override fun isEnabledForCaret(editor: Editor, caret: Caret, context: DataContext) = getNextBookmark(editor, context) != null
  override fun doExecute(editor: Editor, caret: Caret?, context: DataContext) {
    val bookmark = getNextBookmark(editor, context) ?: return
    val pos = bookmark.line.let { if (0 <= it && it < editor.document.lineCount) LogicalPosition(it, 0) else return }
    editor.selectionModel.removeSelection()
    editor.caretModel.removeSecondaryCarets()
    editor.caretModel.moveToLogicalPosition(pos)
    editor.scrollingModel.scrollTo(pos, ScrollType.CENTER)
  }

  private fun getNextBookmark(editor: Editor, context: DataContext): LineBookmark? {
    if (editor.isOneLineMode) return null
    val file = FileDocumentManager.getInstance().getFile(editor.document) ?: return null
    val manager = BookmarksManager.getInstance(editor.project ?: CommonDataKeys.PROJECT.getData(context)) ?: return null
    val bookmarks = manager.bookmarks.mapNotNullTo(mutableListOf()) { if (it is LineBookmark && it.file == file) it else null }
    if (bookmarks.isNotEmpty()) {
      val line = editor.caretModel.logicalPosition.line
      if (forward) {
        // select next bookmark
        bookmarks.sortBy { it.line }
        for (bookmark in bookmarks) {
          if (bookmark.line > line) return bookmark
        }
      }
      else {
        // select previous bookmark
        bookmarks.sortByDescending { it.line }
        for (bookmark in bookmarks) {
          if (bookmark.line < line) return bookmark
        }
      }
      if (BookmarkOccurrence.cyclic) {
        val bookmark = bookmarks[0]
        if (bookmark.line != line) return bookmark
      }
    }
    return null
  }
}
