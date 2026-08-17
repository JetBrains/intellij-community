// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.ex.DocumentNewOps
import com.intellij.openapi.editor.ex.DocumentOp

internal class DocumentNewOpsImpl : DocumentNewOps {
  override fun createInsertOp(offset: Int, fragment: CharSequence): DocumentOp.Insert {
    require(offset >= 0)
    return InsertImpl(offset, fragment)
  }

  override fun createDeleteOp(offset: Int, length: Int): DocumentOp.Delete {
    require(offset >= 0)
    require(length >= 0)
    return DeleteImpl(offset, length)
  }

  override fun createModStampOp(stamp: Long, incSequence: Boolean): DocumentOp.ModStamp {
    return ModStampImpl(stamp, incSequence)
  }

  override fun createUnmodifiedLinesOp(startLine: Int, endLine: Int, exceptLines: IntArray): DocumentOp.UnmodifiedLines {
    require(startLine >= 0)
    require(endLine >= 0)
    return UnmodifiedLinesImpl(startLine, endLine, exceptLines)
  }

  override fun createOps(): List<DocumentOp> {
    return emptyList()
  }

  override fun createOps(op: DocumentOp): List<DocumentOp> {
    return listOf(op)
  }

  override fun createOps(op1: DocumentOp, op2: DocumentOp): List<DocumentOp> {
    return TwoElementsList(op1, op2) // java.util.List.of(op1, op2) is unavailable in Java 8
  }

  override fun createOps(
    op1: DocumentOp,
    op2: DocumentOp,
    op3: DocumentOp,
  ): List<DocumentOp> {
    return ThreeElementsList(op1, op2, op3)
  }

  override fun createOps(
    op1: DocumentOp,
    op2: DocumentOp,
    op3: DocumentOp,
    op4: DocumentOp,
  ): List<DocumentOp> {
    return FourElementsList(op1, op2, op3, op4)
  }

  override fun createOps(vararg ops: DocumentOp): List<DocumentOp> {
    return ops.toList()
  }

  private class InsertImpl(
    private val offset: Int,
    private val fragment: CharSequence,
  ) : DocumentOp.Insert {
    override fun offset(): Int = offset
    override fun fragment(): CharSequence = fragment
  }

  private class DeleteImpl(
    private val offset: Int,
    private val length: Int,
  ) : DocumentOp.Delete {
    override fun offset(): Int = offset
    override fun length(): Int = length
  }

  private class ModStampImpl(
    private val stamp: Long,
    private val incSequence: Boolean,
  ) : DocumentOp.ModStamp {
    override fun modStamp(): Long = stamp
    override fun incSequence(): Boolean = incSequence
  }

  private class UnmodifiedLinesImpl(
    private val startLine: Int,
    private val endLine: Int,
    private val exceptLines: IntArray,
  ) : DocumentOp.UnmodifiedLines {
    override fun startLine(): Int = startLine
    override fun endLine(): Int = endLine
    override fun exceptLines(): IntArray = exceptLines
  }

  private class TwoElementsList(
    private val e0: DocumentOp,
    private val e1: DocumentOp,
  ) : AbstractList<DocumentOp>(), RandomAccess {
    override val size: Int
      get() = 2

    override fun get(index: Int): DocumentOp = when (index) {
      0 -> e0
      1 -> e1
      else -> throw IndexOutOfBoundsException("index: $index, size: 2")
    }
  }

  private class ThreeElementsList(
    private val e0: DocumentOp,
    private val e1: DocumentOp,
    private val e2: DocumentOp,
  ) : AbstractList<DocumentOp>(), RandomAccess {
    override val size: Int
      get() = 3

    override fun get(index: Int): DocumentOp = when (index) {
      0 -> e0
      1 -> e1
      2 -> e2
      else -> throw IndexOutOfBoundsException("index: $index, size: 3")
    }
  }

  private class FourElementsList(
    private val e0: DocumentOp,
    private val e1: DocumentOp,
    private val e2: DocumentOp,
    private val e3: DocumentOp,
  ) : AbstractList<DocumentOp>(), RandomAccess {
    override val size: Int
      get() = 4

    override fun get(index: Int): DocumentOp = when (index) {
      0 -> e0
      1 -> e1
      2 -> e2
      3 -> e3
      else -> throw IndexOutOfBoundsException("index: $index, size: 4")
    }
  }
}
