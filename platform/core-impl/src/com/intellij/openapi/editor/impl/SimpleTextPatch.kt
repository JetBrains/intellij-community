// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.ex.DocumentNewOps
import com.intellij.openapi.editor.ex.DocumentOp
import com.intellij.openapi.editor.ex.DocumentTextPatch
import com.intellij.util.ArrayUtil

internal data class DocumentMarkerEdit(
  val startOffset: Int,
  val endOffset: Int,
  val newLength: Int,
  val originStartOffset: Int,
  val originEndOffset: Int,
  val moveOffset: Int,
)

internal interface DocumentOpMarkerEdit {
  val markerEdit: DocumentMarkerEdit?
}

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
  final override fun toOps(): List<DocumentOp> = patchToOps(this)
  override fun originStartOffset(): Int = startOffset
  override fun originEndOffset(): Int = endOffset
  override fun moveOffset(): Int = startOffset

  private fun patchToOps(patch: DocumentTextPatch): List<DocumentOp> {
    val newOps = DocumentNewOps.getInstance()
    val startOffset = patch.startOffset()
    val endOffset = patch.endOffset()
    val newFragment = patch.newFragment()
    val newFragmentLength = newFragment.length
    val modStampOp = newOps.createModStampOp(newModStamp, false)
    if (startOffset == endOffset && newFragmentLength == 0) {
      return if (clearLineFlags) {
        newOps.createOps(modStampOp, clearLineFlagsOp())
      } else {
        newOps.createOps(modStampOp)
      }
    }
    if (startOffset == endOffset) {
      return if (clearLineFlags) {
        newOps.createOps(
          createInsertOp(newOps, patch, startOffset, newFragment),
          modStampOp,
          clearLineFlagsOp(),
        )
      } else {
        newOps.createOps(
          createInsertOp(newOps, patch, startOffset, newFragment),
          modStampOp,
        )
      }
    }
    if (newFragmentLength == 0) {
      return if (clearLineFlags) {
        newOps.createOps(
          newOps.createDeleteOp(startOffset, endOffset - startOffset),
          modStampOp,
          clearLineFlagsOp(),
        )
      } else {
        newOps.createOps(
          newOps.createDeleteOp(startOffset, endOffset - startOffset),
          modStampOp,
        )
      }
    }
    return if (clearLineFlags) {
      newOps.createOps(
        createDeferredDeleteOp(newOps, startOffset, endOffset - startOffset),
        createInsertOp(newOps, patch, startOffset, newFragment),
        modStampOp,
        clearLineFlagsOp(),
      )
    } else {
      newOps.createOps(
        createDeferredDeleteOp(newOps, startOffset, endOffset - startOffset),
        createInsertOp(newOps, patch, startOffset, newFragment),
        modStampOp,
      )
    }
  }

  private fun createInsertOp(
    newOps: DocumentNewOps,
    patch: DocumentTextPatch,
    offset: Int,
    fragment: CharSequence,
  ): DocumentOp.Insert {
    val op = newOps.createInsertOp(offset, fragment)
    val markerEdit = DocumentMarkerEdit(
      startOffset = patch.startOffset(),
      endOffset = patch.endOffset(),
      newLength = patch.newFragment().length,
      originStartOffset = patch.originStartOffset(),
      originEndOffset = patch.originEndOffset(),
      moveOffset = patch.moveOffset(),
    )
    if (markerEdit.startOffset == offset &&
        markerEdit.endOffset == offset &&
        markerEdit.newLength == fragment.length &&
        markerEdit.originStartOffset == offset &&
        markerEdit.originEndOffset == offset &&
        markerEdit.moveOffset == offset) {
      return op
    }
    return PatchInsertOp(op, markerEdit)
  }

  private fun createDeferredDeleteOp(newOps: DocumentNewOps, offset: Int, length: Int): DocumentOp.Delete {
    return PatchDeleteOp(newOps.createDeleteOp(offset, length))
  }

  private fun clearLineFlagsOp(): DocumentOp.UnmodifiedLines {
    val newOps = DocumentNewOps.getInstance()
    return newOps.createUnmodifiedLinesOp(0, Int.MAX_VALUE, ArrayUtil.EMPTY_INT_ARRAY)
  }

  private class PatchInsertOp(
    private val delegate: DocumentOp.Insert,
    override val markerEdit: DocumentMarkerEdit,
  ) : DocumentOp.Insert by delegate, DocumentOpMarkerEdit

  private class PatchDeleteOp(
    private val delegate: DocumentOp.Delete,
  ) : DocumentOp.Delete by delegate, DocumentOpMarkerEdit {
    override val markerEdit: DocumentMarkerEdit? = null
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
