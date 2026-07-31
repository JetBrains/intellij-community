// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.ex.DocumentTextPatch

internal open class SimpleTextPatch(
  private val startOffset: Int,
  private val endOffset: Int,
  private val newFragment: CharSequence,
  private val newModStamp: Long,
  private val clearLineFlags: Boolean,
  private val clearModTree: Boolean,
) : DocumentTextPatch {
  final override fun startOffset(): Int = startOffset
  final override fun endOffset(): Int = endOffset
  final override fun newFragment(): CharSequence = newFragment
  final override fun newModStamp(): Long = newModStamp
  final override fun clearLineFlags(): Boolean = clearLineFlags
  final override fun clearModTree(): Boolean = clearModTree
  override fun originStartOffset(): Int = startOffset
  override fun originEndOffset(): Int = endOffset
  override fun moveOffset(): Int = startOffset

  final override fun toString(): String {
    return "${javaClass.simpleName}(" +
           "startOffset=${startOffset()}" +
           ", endOffset=${endOffset()}" +
           ", newFragment.length=${newFragment().length}" +
           ", originStartOffset=${originStartOffset()}" +
           ", originEndOffset=${originEndOffset()}" +
           ", moveOffset=${moveOffset()}" +
           ", newModStamp=${newModStamp()}" +
           ", clearLineFlags=${clearLineFlags()}" +
           ", clearModTree=${clearModTree()}" +
           ")"
  }
}

internal class ComplexTextPatch(
  startOffset: Int,
  endOffset: Int,
  newFragment: CharSequence,
  newModStamp: Long,
  clearLineFlags: Boolean,
  clearModTree: Boolean,
  private val originStartOffset: Int,
  private val originEndOffset: Int,
  private val moveOffset: Int,
) : SimpleTextPatch(
  startOffset,
  endOffset,
  newFragment,
  newModStamp,
  clearLineFlags,
  clearModTree,
) {
  override fun originStartOffset(): Int = originStartOffset
  override fun originEndOffset(): Int = originEndOffset
  override fun moveOffset(): Int = moveOffset
}
