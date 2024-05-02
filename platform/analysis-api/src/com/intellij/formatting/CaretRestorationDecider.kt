// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting

import com.intellij.lang.LanguageExtension
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import org.jetbrains.annotations.ApiStatus

/**
 * Extension point which decides if the caret should be restored after formatting or not.
 * Implement this if there is a need to restrict a restoration of caret position after formatting for concrete language
 * @see CaretPositionKeeper
 */
@ApiStatus.Experimental
interface CaretRestorationDecider {
  /**
   * Determines whether the caret position should be restored after formatting.
   * @param document the document being formatted
   * @param editor the editor in which the document is being displayed
   * @param caretOffset offset in which the caret is located.
   * Note that it might be different from ```editor.caretModel.caretOffset``` due to the late synchronization.
   * @return true if the caret position should be restored, false otherwise
   */
  fun shouldRestoreCaret(document: Document, editor: Editor, caretOffset: Int): Boolean

  companion object : LanguageExtension<CaretRestorationDecider>("com.intellij.formatting.caretRestorationDecider")
}