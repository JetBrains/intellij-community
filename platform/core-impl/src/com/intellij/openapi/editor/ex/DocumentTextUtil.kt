// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("DocumentTextUtil")
@file:ApiStatus.Internal

package com.intellij.openapi.editor.ex

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Contract

/**
 * Returns the text with [op] applied.
 * The move offset of [op] does not change the text.
 */
@Contract(pure = true)
fun DocumentText.applyOp(op: DocumentOp.Text): DocumentText {
  return when (op) {
    is DocumentOp.Insert -> insert(op.offset(), op.fragment())
    is DocumentOp.Delete -> delete(op.offset(), op.offset() + op.length())
  }
}

@Contract(pure = true)
fun DocumentText.getLineStartOffsetAt(offset: Int): Int {
  if (offset < 0 || offset > length()) {
    return offset
  }
  val lineNumber = lineNumber(offset)
  return lineStartOffset(lineNumber)
}

@Contract(pure = true)
fun DocumentText.getLineEndOffsetAt(offset: Int): Int {
  if (offset < 0 || offset > length()) {
    return offset
  }
  val lineNumber = lineNumber(offset)
  return lineEndOffset(lineNumber)
}

@Contract(pure = true)
fun DocumentText.alignToCodePointBoundary(offset: Int): Int {
  return if (isInsideSurrogatePair(offset)) offset - 1 else offset
}

@Contract(pure = true)
fun DocumentText.getPreviousCodePointOffset(offset: Int): Int {
  return offset - (if (isSurrogatePair(offset - 2)) 2 else 1)
}

@Contract(pure = true)
fun DocumentText.isInsideSurrogatePair(offset: Int): Boolean {
  return isSurrogatePair(offset - 1)
}

@Contract(pure = true)
fun DocumentText.isSurrogatePair(offset: Int): Boolean {
  val chars: CharSequence = luckyChars()
  return offset >= 0 && offset + 1 < chars.length &&
         Character.isHighSurrogate(chars[offset]) &&
         Character.isLowSurrogate(chars[offset + 1])
}
