// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.ex.DocumentText
import com.intellij.openapi.editor.ex.DocumentTextPatch
import com.intellij.util.text.ImmutableCharSequence

internal open class SimpleTextPatch(
  private val startOffset: Int,
  private val endOffset: Int,
  newFragment: CharSequence,
  private val newModStamp: Long,
  private val clearLineFlags: Boolean,
) : DocumentTextPatch {
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

  final override fun startOffset(): Int = startOffset
  final override fun endOffset(): Int = endOffset
  final override fun newFragment(): CharSequence = newFragment
  final override fun newModStamp(): Long = newModStamp
  final override fun clearLineFlags(): Boolean = clearLineFlags
  override fun originStartOffset(): Int = startOffset
  override fun originEndOffset(): Int = endOffset
  override fun moveOffset(): Int = startOffset

  final override fun toString(): String {
    return "${javaClass.simpleName}(" +
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

internal class ComplexTextPatch(
  startOffset: Int,
  endOffset: Int,
  newFragment: CharSequence,
  newModStamp: Long,
  clearLineFlags: Boolean,
  private val originStartOffset: Int,
  private val originEndOffset: Int,
  private val moveOffset: Int,
) : SimpleTextPatch(
  startOffset,
  endOffset,
  newFragment,
  newModStamp,
  clearLineFlags,
) {
  override fun originStartOffset(): Int = originStartOffset
  override fun originEndOffset(): Int = originEndOffset
  override fun moveOffset(): Int = moveOffset
}
