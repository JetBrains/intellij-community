// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.ex.DocumentOp
import com.intellij.openapi.editor.ex.DocumentText
import com.intellij.openapi.editor.ex.DocumentPatch
import com.intellij.util.ArrayUtil
import com.intellij.util.text.ImmutableCharSequence

internal class DocumentPatchImpl(
  private val startOffset: Int,
  private val endOffset: Int,
  newFragment: CharSequence,
  private val newModStamp: Long,
  private val clearLineFlags: Boolean,
  private val originStartOffset: Int,
  private val originEndOffset: Int,
  private val moveOffset: Int,
) : DocumentPatch {
  private val newFragment: CharSequence = ImmutableCharSequence.asImmutable(newFragment)

  @Volatile
  private var cachedLineDiff: DocumentLineDiff? = null

  internal fun attachLineDiffCache(lineDiff: DocumentLineDiff) {
    synchronized(this) {
      check(cachedLineDiff == null || cachedLineDiff === lineDiff) {
        "DocumentTextPatch is already associated with another line diff"
      }
      cachedLineDiff = lineDiff
    }
  }

  internal fun getOrCreateLineDiff(beforeText: DocumentText): DocumentLineDiff {
    cachedLineDiff?.let { return it }
    return synchronized(this) {
      cachedLineDiff ?: DocumentLineDiff(
        changeStartOffset = startOffset,
        oldFragment = beforeText.chars().subSequence(startOffset, endOffset),
        newFragment = newFragment,
      ).also { cachedLineDiff = it }
    }
  }

  override fun startOffset(): Int = startOffset
  override fun endOffset(): Int = endOffset
  override fun newFragment(): CharSequence = newFragment
  override fun newModStamp(): Long = newModStamp
  override fun clearLineFlags(): Boolean = clearLineFlags
  override fun originStartOffset(): Int = originStartOffset
  override fun originEndOffset(): Int = originEndOffset
  override fun moveOffset(): Int = moveOffset
  override fun ops(): List<DocumentOp> = patchToOps(this)

  private fun patchToOps(patch: DocumentPatch): List<DocumentOp> {
    val startOffset = patch.startOffset()
    val endOffset = patch.endOffset()
    val newFragment = patch.newFragment()
    val newModStamp = patch.newModStamp()
    val clearLineFlags = patch.clearLineFlags()
    var moveOffset = patch.moveOffset()
    if (endOffset > startOffset && newFragment.isNotEmpty()) {
      // A replace is not half of a move. A whole-text replace keeps the move offset of its narrowed event.
      moveOffset = startOffset
    }
    val ops = ArrayList<DocumentOp>(4)
    if (endOffset > startOffset) {
      ops.add(DocumentOp.deleteOp(startOffset, endOffset - startOffset, moveOffset))
    }
    if (newFragment.isNotEmpty()) {
      ops.add(DocumentOp.insertOp(startOffset, newFragment, moveOffset))
    }
    ops.add(DocumentOp.modStampOp(newModStamp, false))
    if (clearLineFlags) {
      ops.add(DocumentOp.cleanLinesOp(0, Int.MAX_VALUE, ArrayUtil.EMPTY_INT_ARRAY))
    }
    return ops
  }

  override fun toString(): String {
    return "DocumentPatch(" +
           "startOffset=${startOffset()}" +
           ", endOffset=${endOffset()}" +
           ", newFragment.length=${newFragment().length}" +
           (if (originStartOffset() == startOffset()) "" else ", originStartOffset=${originStartOffset()}") +
           (if (originEndOffset() == endOffset()) "" else ", originEndOffset=${originEndOffset()}") +
           (if (moveOffset() == startOffset()) "" else ", moveOffset=${moveOffset()}") +
           ", newModStamp=${newModStamp()}" +
           ", clearLineFlags=${clearLineFlags()}" +
           ")"
  }
}
