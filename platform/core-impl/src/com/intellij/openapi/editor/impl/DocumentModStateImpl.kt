// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.ex.DocumentModState
import com.intellij.openapi.editor.ex.DocumentText
import com.intellij.openapi.editor.ex.DocumentTextPatch
import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntList

internal class DocumentModStateImpl private constructor(
  private val modStamp: Long,
  private val modSequence: Int,
  private var modifiedLines: ModifiedLineSet?, // non-volatile intentionally, see getModifiedLines()
) : DocumentModState {

  constructor() : this(
    DocumentModStamp.next(),
    0,
    null,
  )

  override fun stamp(): Long {
    return modStamp
  }

  override fun sequence(): Int {
    return modSequence
  }

  override fun isLineModified(line: Int): Boolean {
    val modifiedLines = this.modifiedLines
    return modifiedLines != null && modifiedLines.isModified(line)
  }

  override fun withPatch(before: DocumentText, after: DocumentText, diff: DocumentTextPatch): DocumentModState {
    val oldModifiedLines = getModifiedLines(before)
    var newModifiedLines = oldModifiedLines.update(
      before,
      diff.startOffset(),
      diff.endOffset(),
      diff.newFragment(),
    )
    if (diff.clearLineFlags()) {
      newModifiedLines = newModifiedLines.clearModificationFlags(0, Int.MAX_VALUE)
    }
    assert(newModifiedLines.lineCount == after.lineCount()) {
      "after.lineCount() = " + after.lineCount() + "; modState.lineCount() = " + newModifiedLines.lineCount
    }
    return DocumentModStateImpl(diff.newModStamp(), modSequence + 1, newModifiedLines)
  }

  override fun withStamp(newStamp: Long, incrementSequence: Boolean): DocumentModState {
    val newSequence = if (incrementSequence) {
      modSequence + 1
    } else {
      modSequence
    }
    if (modStamp == newStamp && modSequence == newSequence) {
      return this
    }
    return DocumentModStateImpl(newStamp, newSequence, modifiedLines)
  }

  override fun withClearedLineFlags(
    text: DocumentText,
    startLine: Int,
    endLine: Int,
    exceptLines: IntArray,
  ): DocumentModState {
    if (this.modifiedLines == null) {
      // there were no text changes if line set is not created yet
      return this
    }
    var modifiedLines = getModifiedLines(text)
    val modifiedLineIndices: IntList
    if (exceptLines.isEmpty()) {
      modifiedLineIndices = EMPTY_INDICES
    } else {
      modifiedLineIndices = IntArrayList(exceptLines.size)
      for (line in exceptLines) {
        // TODO: why line < 0 || line >= modifiedLines.lineCount
        //  silently ignored not IndexOutOfBoundsException?
        if (0 <= line && line < modifiedLines.lineCount) {
          if (modifiedLines.isModified(line)) {
            modifiedLineIndices.add(line)
          }
        }
      }
    }
    modifiedLines = modifiedLines.clearModificationFlags(startLine, endLine)
    modifiedLines = modifiedLines.setModified(modifiedLineIndices)
    assert(modifiedLines.lineCount == text.lineCount()) {
      "text.lineCount() = " + text.lineCount() + "; modState.lineCount() = " + modifiedLines.lineCount
    }
    return DocumentModStateImpl(modStamp, modSequence, modifiedLines)
  }

  override fun withMetadata(other: DocumentModState): DocumentModState {
    if (this === other) {
      return this
    }
    val otherStamp = other.stamp()
    val otherSequence = other.sequence()
    if (modStamp == otherStamp && modSequence == otherSequence) {
      return this
    }
    return DocumentModStateImpl(otherStamp, otherSequence, modifiedLines)
  }

  private fun getModifiedLines(text: DocumentText): ModifiedLineSet {
    var modifiedLines = this.modifiedLines
    if (modifiedLines != null) {
      return modifiedLines
    }
    modifiedLines = ModifiedLineSet.create(text.cachedChars())
    this.modifiedLines = modifiedLines
    return modifiedLines
  }

  override fun toString(): String {
    val id = Integer.toHexString(System.identityHashCode(this))
    val ml = modifiedLines?.let { "@" + Integer.toHexString(System.identityHashCode(it)) } ?: "null"
    return "DocumentModState" + id + "{stamp=" + modStamp + ", sequence=" + modSequence + ", modifiedLines=" + ml + "}"
  }

  companion object {
    private val EMPTY_INDICES: IntList = IntArrayList(0)
  }
}
