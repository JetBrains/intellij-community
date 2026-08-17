// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.ex

import com.intellij.openapi.editor.impl.DocumentNewOpsImpl
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface DocumentNewOps {
  fun createInsertOp(offset: Int, fragment: CharSequence): DocumentTextOp.Insert
  fun createDeleteOp(offset: Int, length: Int): DocumentTextOp.Delete
  fun createOps(): List<DocumentTextOp>
  fun createOps(op: DocumentTextOp): List<DocumentTextOp>
  fun createOps(op1: DocumentTextOp, op2: DocumentTextOp): List<DocumentTextOp>
  fun createOps(vararg ops: DocumentTextOp): List<DocumentTextOp>

  companion object {
    private val INSTANCE: DocumentNewOps = DocumentNewOpsImpl()

    @JvmStatic
    fun getInstance(): DocumentNewOps {
      return INSTANCE
    }
  }
}
