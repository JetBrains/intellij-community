// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.ex.DocumentNewOps
import com.intellij.openapi.editor.ex.DocumentTextOp
import com.intellij.openapi.editor.ex.DocumentTextPatch

internal open class SimpleTextPatch(
  private val startOffset: Int,
  private val endOffset: Int,
  private val newFragment: CharSequence,
  private val newModStamp: Long,
  private val clearLineFlags: Boolean,
) : DocumentTextPatch {
  final override fun startOffset(): Int = startOffset
  final override fun endOffset(): Int = endOffset
  final override fun newFragment(): CharSequence = newFragment
  final override fun newModStamp(): Long = newModStamp
  final override fun clearLineFlags(): Boolean = clearLineFlags
  final override fun toOps(): List<DocumentTextOp> = patchToOps(this)
  override fun originStartOffset(): Int = startOffset
  override fun originEndOffset(): Int = endOffset
  override fun moveOffset(): Int = startOffset

  private fun patchToOps(patch: DocumentTextPatch): List<DocumentTextOp> {
    val newOps = DocumentNewOps.getInstance()
    val startOffset = patch.startOffset()
    val endOffset = patch.endOffset()
    val newFragment = patch.newFragment()
    val newFragmentLength = newFragment.length
    if (startOffset == endOffset && newFragmentLength == 0) {
      return newOps.createOps()
    }
    if (startOffset == endOffset) {
      return newOps.createOps(newOps.createInsertOp(startOffset, newFragment))
    }
    if (newFragmentLength == 0) {
      return newOps.createOps(newOps.createDeleteOp(startOffset, endOffset - startOffset))
    }
    return newOps.createOps(
      newOps.createDeleteOp(startOffset, endOffset - startOffset),
      newOps.createInsertOp(startOffset, newFragment),
    )
  }

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
