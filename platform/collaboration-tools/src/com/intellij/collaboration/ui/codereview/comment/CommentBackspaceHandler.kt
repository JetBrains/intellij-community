// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.comment

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.editorActions.BackspaceHandlerDelegate
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus

/**
 * Handles backspace in Markdown review comments to force showing a completion popup when a user edits a mention.
 * For example, we have some completion variants for the prefix "@a" and nothing for the prefix "@ab".
 * You type "@ab", the completion dropdown disappears, and then you press backspace to remove the last character.
 * The popup should reappear then, so the handler is responsible for reopening the popup.
 */
@ApiStatus.Internal
abstract class CommentBackspaceHandler<T> : BackspaceHandlerDelegate() {
  override fun beforeCharDeleted(c: Char, file: PsiFile, editor: Editor) {
  }

  override fun charDeleted(c: Char, file: PsiFile, editor: Editor): Boolean {
    val project = editor.project ?: return false
    if (editor.getUserData(getKey()) == null) return false

    if (LookupManager.getActiveLookup(editor) == null && isCaretOnUserMention(editor)) {
      AutoPopupController.getInstance(project).scheduleAutoPopup(editor)
    }

    return false
  }

  /**
   * @return an instance of a user data key that marks a review comment editor
   */
  abstract fun getKey(): Key<T>

  /**
   * @return true if the character is a valid mention character for this comment
   */
  abstract fun isValidMentionCharacter(c: Char): Boolean

  private fun isCaretOnUserMention(editor: Editor): Boolean {
    val document = editor.document
    val offset = editor.caretModel.offset

    val chars = document.charsSequence
    var i = offset
    while (i > 0 && isValidMentionCharacter(chars[i - 1])) {
      i--
    }
    return i < offset && chars[i] == '@'
  }
}
