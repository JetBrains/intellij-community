// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.ex.DocumentNewOps
import com.intellij.openapi.editor.ex.DocumentTextOp

internal class DocumentNewOpsImpl : DocumentNewOps {
  override fun createInsertOp(offset: Int, fragment: CharSequence): DocumentTextOp.Insert {
    require(offset >= 0)
    return InsertImpl(offset, fragment)
  }

  override fun createDeleteOp(offset: Int, length: Int): DocumentTextOp.Delete {
    require(offset >= 0)
    require(length >= 0)
    return Delete((offset.toLong() shl 32) or (length.toLong() and 0xffffffffL))
  }

  override fun createOps(): List<DocumentTextOp> {
    return emptyList()
  }

  override fun createOps(op: DocumentTextOp): List<DocumentTextOp> {
    return listOf(op)
  }

  override fun createOps(op1: DocumentTextOp, op2: DocumentTextOp): List<DocumentTextOp> {
    return TwoElementsList(op1, op2) // java.util.List.of(op1, op2) is unavailable in Java 8
  }

  override fun createOps(vararg ops: DocumentTextOp): List<DocumentTextOp> {
    return ops.toList()
  }

  private class InsertImpl(
    private val offset: Int,
    private val fragment: CharSequence,
  ) : DocumentTextOp.Insert {
    override fun offset(): Int = offset
    override fun fragment(): CharSequence = fragment
  }

  @JvmInline
  private value class Delete(
    private val packed: Long,
  ) : DocumentTextOp.Delete {
    override fun offset(): Int = (packed shr 32).toInt()
    override fun length(): Int = packed.toInt()
  }

  private class TwoElementsList<E>(
    private val e0: E,
    private val e1: E,
  ) : AbstractList<E>(), RandomAccess {
    override val size: Int
      get() = 2

    override fun get(index: Int): E = when (index) {
      0 -> e0
      1 -> e1
      else -> throw IndexOutOfBoundsException("index: $index, size: 2")
    }
  }
}
