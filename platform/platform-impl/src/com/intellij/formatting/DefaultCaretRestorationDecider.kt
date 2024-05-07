// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.util.text.CharArrayUtil

object DefaultCaretRestorationDecider: CaretRestorationDecider {
  override fun shouldRestoreCaret(document: Document, editor: Editor, caretOffset: Int): Boolean {
    val lineStartOffset = CaretPositionKeeper.getLineStartOffsetByTotalOffset(document, caretOffset)
    val lineEndOffset = CaretPositionKeeper.getLineEndOffsetByTotalOffset(document, caretOffset)
    return CharArrayUtil.isEmptyOrSpaces(document.charsSequence, lineStartOffset, lineEndOffset)
  }
}