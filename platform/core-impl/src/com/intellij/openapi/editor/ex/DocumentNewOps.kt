// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.ex

import com.intellij.openapi.editor.impl.DocumentNewOpsImpl
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface DocumentNewOps {
  fun createInsertOp(offset: Int, fragment: CharSequence): DocumentOp.Insert
  fun createDeleteOp(offset: Int, length: Int): DocumentOp.Delete
  fun createModStampOp(stamp: Long, incSequence: Boolean): DocumentOp.ModStamp
  fun createUnmodifiedLinesOp(startLine: Int, endLine: Int, exceptLines: IntArray): DocumentOp.UnmodifiedLines

  fun createOps(): List<DocumentOp>
  fun createOps(op: DocumentOp): List<DocumentOp>
  fun createOps(op1: DocumentOp, op2: DocumentOp): List<DocumentOp>
  fun createOps(op1: DocumentOp, op2: DocumentOp, op3: DocumentOp): List<DocumentOp>
  fun createOps(op1: DocumentOp, op2: DocumentOp, op3: DocumentOp, op4: DocumentOp): List<DocumentOp>
  fun createOps(vararg ops: DocumentOp): List<DocumentOp>

  companion object {
    private val INSTANCE: DocumentNewOps = DocumentNewOpsImpl()

    @JvmStatic
    fun getInstance(): DocumentNewOps {
      return INSTANCE
    }
  }
}
