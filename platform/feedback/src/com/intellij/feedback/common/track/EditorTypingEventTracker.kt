// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback.common.track

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.feedback.common.IdleFeedbackTypeResolver.checkActivity
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