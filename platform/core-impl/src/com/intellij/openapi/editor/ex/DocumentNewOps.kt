// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.ex

import com.intellij.openapi.editor.impl.DocumentNewOpsImpl
import com.intellij.openapi.util.Key
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface DocumentNewOps {
  fun createModStampOp(stamp: Long, incSequence: Boolean): DocumentOp.ModStamp
  fun createUnmodifiedLinesOp(startLine: Int, endLine: Int, exceptLines: IntArray): DocumentOp.UnmodifiedLines
  fun <S : DocumentSputnik> createSetSputnikOp(key: Key<S>, sputnik: S?): DocumentOp.SetSputnik

  companion object {
    private val INSTANCE: DocumentNewOps = DocumentNewOpsImpl()

    @JvmStatic
    fun getInstance(): DocumentNewOps {
      return INSTANCE
    }
  }
}
