// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.ex.DocumentOp
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.text.ImmutableCharSequence

internal class InsertOpImpl(
  private val offset: Int,
  fragment: CharSequence,
  private val moveOffset: Int,
) : DocumentOp.Insert {
  /**
   * A copy detaches the op from a mutable CharSequence the caller may keep.
   * It is free for a sequence that is already immutable, and a String is the usual case.
   */
  private val fragment: CharSequence = ImmutableCharSequence.asImmutable(fragment)

  override fun offset(): Int = offset
  override fun length(): Int = fragment.length
  override fun fragment(): CharSequence = fragment
  override fun moveOffset(): Int = moveOffset

  override fun equals(other: Any?): Boolean {
    return other is InsertOpImpl &&
           offset == other.offset &&
           moveOffset == other.moveOffset &&
           fragment.contentEquals(other.fragment)
  }

  override fun hashCode(): Int {
    val offsetsHash = 31 * offset + moveOffset
    return 31 * offsetsHash + StringUtil.stringHashCode(fragment)
  }

  override fun toString(): String {
    return "ins($offset, ${fragment.quotedForMessage()}${moveSuffix(this)})"
  }
}

internal class DeleteOpImpl(
  private val offset: Int,
  private val length: Int,
  private val moveOffset: Int,
) : DocumentOp.Delete {
  override fun offset(): Int = offset
  override fun length(): Int = length
  override fun moveOffset(): Int = moveOffset

  override fun equals(other: Any?): Boolean {
    return other is DeleteOpImpl &&
           offset == other.offset &&
           length == other.length &&
           moveOffset == other.moveOffset
  }

  override fun hashCode(): Int {
    val offsetsHash = 31 * offset + moveOffset
    return 31 * offsetsHash + length
  }

  override fun toString(): String {
    return "del($offset, len=$length${moveSuffix(this)})"
  }
}

/**
 * Whether this op is half of a text move. See [DocumentOp.Text.moveOffset].
 */
internal fun DocumentOp.Text.isMove(): Boolean {
  return moveOffset() != offset()
}

/**
 * Whether the moved text at [DocumentOp.Text.moveOffset] lies apart from the text that this op
 * changes. Both offsets must not be negative.
 */
internal fun DocumentOp.Text.isMovedTextApart(): Boolean {
  val length = length()
  val endsBefore = moveOffset() <= offset() - length
  val startsAfter = moveOffset() - length >= offset()
  return endsBefore || startsAfter
}

private fun moveSuffix(op: DocumentOp.Text): String {
  if (op.isMove()) {
    return ", move=${op.moveOffset()}"
  }
  return ""
}

internal class ModStampOpImpl(
  private val stamp: Long,
  private val incSequence: Boolean,
) : DocumentOp.ModStamp {
  override fun modStamp(): Long = stamp
  override fun incSequence(): Boolean = incSequence

  override fun toString(): String {
    return "modStamp($stamp, incSequence=$incSequence)"
  }
}

internal class CleanLinesOpImpl(
  private val startLine: Int,
  private val endLine: Int,
  private val exceptLines: IntArray,
) : DocumentOp.CleanLines {
  init {
    require(startLine >= 0)
    require(endLine >= 0)
  }

  override fun startLine(): Int = startLine
  override fun endLine(): Int = endLine
  override fun exceptLines(): IntArray = exceptLines

  override fun toString(): String {
    return "cleanLines($startLine, $endLine, exceptLines=${exceptLines.contentToString()})"
  }
}
