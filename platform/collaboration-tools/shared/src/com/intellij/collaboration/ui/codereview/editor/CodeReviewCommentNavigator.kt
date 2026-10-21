// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.editor

import com.intellij.collaboration.util.RefComparisonChange
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorOpenRequest
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus

/**
 * The navigation to a comment thread from one editor.
 * Use [CodeReviewCommentNavigator.forEditor] to get it.
 */
@ApiStatus.Internal
fun interface CodeReviewCommentNavigation {
  /**
   * Shows the line [lineIdx] of the file after [change] and does not take focus.
   * The caller requests the thread focus separately.
   * The thread UI processes that request when it is shown.
   */
  @RequiresEdt
  fun navigate(change: RefComparisonChange, lineIdx: Int)
}

/**
 * Shows the line of a comment thread in the editor, and does not take focus from the thread.
 */
@ApiStatus.Internal
class CodeReviewCommentNavigator(private val project: Project) {
  /**
   * Shows the line [lineIdx] of the file after [change].
   * When it is the file of [editor], this moves the caret in [editor].
   * Otherwise, this opens the file without focus.
   */
  @RequiresEdt
  fun navigate(editor: Editor, change: RefComparisonChange, lineIdx: Int) {
    val file = change.filePathAfter?.virtualFile ?: return
    val descriptor = OpenFileDescriptor(project, file, lineIdx, 0)
    if (file == FileDocumentManager.getInstance().getFile(editor.document)) {
      descriptor.navigateIn(editor)
    }
    else {
      FileEditorManager.getInstance(project).requestOpenEditor(descriptor, FileEditorOpenRequest.fromDescriptor(descriptor))
    }
  }

  /**
   * Binds the navigation to [editor], so that a review model does not need the editor.
   */
  fun forEditor(editor: Editor): CodeReviewCommentNavigation =
    CodeReviewCommentNavigation { change, lineIdx -> navigate(editor, change, lineIdx) }
}
