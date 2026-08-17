// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.ex.DocumentModState
import com.intellij.openapi.editor.ex.DocumentText
import com.intellij.openapi.editor.ex.DocumentOp
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

  override fun applyOp(before: DocumentText, after: DocumentText, op: DocumentOp): DocumentModState {
    return when (op) {
      is DocumentOp.Insert -> applyInsert(before, after, op)
      is DocumentOp.Delete -> applyDelete(before, after, op)
      is DocumentOp.ModStamp -> applyModStamp(op)
      is DocumentOp.UnmodifiedLines -> applyUnmodifiedLines(before, op)
    }
  }

  private fun applyInsert(before: DocumentText, after: DocumentText, op: DocumentOp.Insert): DocumentModState {
    val offset = op.offset()
    val fragment = op.fragment()
    if (fragment.isEmpty()) {
      return this
    }
    val oldModifiedLines = getModifiedLines(before)
    val newModifiedLines = oldModifiedLines.update(
      before,
      offset,
      offset,
      fragment,
    )
    assert(newModifiedLines.lineCount == after.lineCount()) {
      "after.lineCount() = " + after.lineCount() + "; modState.lineCount() = " + newModifiedLines.lineCount
    }
    return DocumentModStateImpl(modStamp, modSequence + 1, newModifiedLines)
  }

  private fun applyDelete(before: DocumentText, after: DocumentText, op: DocumentOp.Delete): DocumentModState {
    val offset = op.offset()
    val length = op.length()
    if (length == 0) {
      return this
    }
    val oldModifiedLines = getModifiedLines(before)
    val newModifiedLines = oldModifiedLines.update(
      before,
      offset,
      offset + length,
      "",
    )
    assert(newModifiedLines.lineCount == after.lineCount()) {
      "after.lineCount() = " + after.lineCount() + "; modState.lineCount() = " + newModifiedLines.lineCount
    }
    return DocumentModStateImpl(modStamp, modSequence + 1, newModifiedLines)
  }

  private fun applyModStamp(op: DocumentOp.ModStamp): DocumentModState {
    val newModSeq = if (op.incSequence()) {
      modSequence + 1
    } else {
      modSequence
    }
    val newModStamp = op.modStamp()
    if (newModStamp == modStamp && newModSeq == modSequence) {
      return this
    }
    return DocumentModStateImpl(newModStamp, newModSeq, modifiedLines)
  }

  private fun applyUnmodifiedLines(text: DocumentText, op: DocumentOp.UnmodifiedLines): DocumentModState {
    if (this.modifiedLines == null) {
      // there were no text changes if line set is not created yet
      return this
    }
    val startLine: Int = op.startLine()
    val endLine: Int = op.endLine()
    val exceptLines: IntArray = op.exceptLines()
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
