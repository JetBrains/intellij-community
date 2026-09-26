// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.ex.DocumentNewOps
import com.intellij.openapi.editor.ex.DocumentOp
import com.intellij.openapi.editor.ex.DocumentSputnik
import com.intellij.openapi.util.Key

internal class DocumentNewOpsImpl : DocumentNewOps {
  override fun createModStampOp(stamp: Long, incSequence: Boolean): DocumentOp.ModStamp {
    return ModStampImpl(stamp, incSequence)
  }

  override fun createUnmodifiedLinesOp(startLine: Int, endLine: Int, exceptLines: IntArray): DocumentOp.UnmodifiedLines {
    require(startLine >= 0)
    require(endLine >= 0)
    return UnmodifiedLinesImpl(startLine, endLine, exceptLines)
  }

  override fun <S : DocumentSputnik> createSetSputnikOp(key: Key<S>, sputnik: S?): DocumentOp.SetSputnik {
    return SetSputnikImpl(key, sputnik)
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

  private class SetSputnikImpl(
    private val key: Key<out DocumentSputnik>,
    private val sputnik: DocumentSputnik?,
  ) : DocumentOp.SetSputnik {
    override fun key(): Key<out DocumentSputnik> = key
    override fun sputnik(): DocumentSputnik? = sputnik
  }
}
