// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.ex

import com.intellij.openapi.util.Key
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
sealed interface DocumentOp {
  interface Insert : DocumentOp {
    fun offset(): Int
    fun fragment(): CharSequence
  }

  interface Delete : DocumentOp {
    fun offset(): Int
    fun length(): Int
  }

  interface ModStamp : DocumentOp {
    fun modStamp(): Long
    fun incSequence(): Boolean
  }

  interface UnmodifiedLines : DocumentOp {
    fun startLine(): Int
    fun endLine(): Int
    fun exceptLines(): IntArray
  }

  interface SetSputnik : DocumentOp {
    fun key(): Key<out DocumentSputnik>
    fun sputnik(): DocumentSputnik?
  }
}
