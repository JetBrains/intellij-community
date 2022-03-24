package com.intellij.feedback.track.active

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.feedback.FeedbackTypeResolver.checkActivity
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

/**
 * Tracks keyboard activity in the editor and display the notification after a long inactivity.
 */

class EditorTypingEventTracker : TypedHandlerDelegate() {
  override fun charTyped(c: Char, project: Project, editor: Editor, file: PsiFile): Result {
    checkActivity(project)
    return Result.CONTINUE
  }
}