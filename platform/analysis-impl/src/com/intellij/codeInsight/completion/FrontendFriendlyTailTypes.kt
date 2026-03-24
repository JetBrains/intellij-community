// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion

import com.intellij.codeInsight.ModNavigatorTailType
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ModNavigator
import com.intellij.util.text.CharArrayUtil
import kotlinx.serialization.Serializable

@Serializable
internal data class FrontendFriendlyCharTailType(
  val char: Char,
  val overwrite: Boolean = true,
) : ModNavigatorTailType(), FrontendFriendlyTailType {
  override fun processTail(navigator: ModNavigator, tailOffset: Int): Int {
    return insertChar(navigator, tailOffset, char, overwrite)
  }

  @Suppress("OVERRIDE_DEPRECATION")
  override fun isApplicable(context: InsertionContext): Boolean {
    return !context.shouldAddCompletionChar() || context.completionChar != char
  }
}

@Serializable
internal object NoneTailType : ModNavigatorTailType(), FrontendFriendlyTailType {
  override fun processTail(navigator: ModNavigator, tailOffset: Int): Int = tailOffset
}

@Serializable
internal object HumbleSpaceBeforeWordTailType : ModNavigatorTailType(), FrontendFriendlyTailType {
  override fun processTail(navigator: ModNavigator, tailOffset: Int): Int {
    val text = navigator.document.charsSequence
    if (text.length > tailOffset + 1 && text[tailOffset] == ' ') {
      val ch = text[tailOffset + 1]
      if (ch == '@' || ch.isLetter()) {
        return tailOffset
      }
    }
    return insertChar(navigator, tailOffset, ' ', false)
  }
}

@Serializable
internal object CondExprColonTailType : ModNavigatorTailType(), FrontendFriendlyTailType {
  override fun processTail(navigator: ModNavigator, tailOffset: Int): Int {
    val document = navigator.document
    val textLength = document.textLength
    val chars = document.charsSequence

    val afterWhitespace = CharArrayUtil.shiftForward(chars, tailOffset, " \n\t")
    if (afterWhitespace < textLength && chars[afterWhitespace] == ':') {
      return moveCaret(navigator, tailOffset, afterWhitespace - tailOffset + 1)
    }
    document.insertString(tailOffset, " : ")
    return moveCaret(navigator, tailOffset, 3)
  }
}

@Serializable
internal object FrontendFriendlyUnknownTailType : ModNavigatorTailType() {
  override fun processTail(navigator: ModNavigator, tailOffset: Int): Int = tailOffset
  override fun processTail(editor: Editor, tailOffset: Int): Int = tailOffset
  override fun toString(): String = "UNKNOWN"
}
