// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.ex.DocumentModState
import com.intellij.openapi.editor.ex.DocumentOp
import com.intellij.openapi.editor.ex.DocumentSnapshot
import com.intellij.openapi.editor.ex.DocumentText
import com.intellij.openapi.editor.ex.applyOp

internal class DocumentSnapshotImpl private constructor(
  private val text: DocumentText,
  private val modState: DocumentModState,
) : DocumentSnapshot {
  constructor(text: DocumentText) : this(
    text = text,
    modState = DocumentModStateImpl(),
  )

  override fun text(): DocumentText {
    return text
  }

  override fun modState(): DocumentModState {
    return modState
  }

  override fun newInstance(): DocumentSnapshotImpl {
    return DocumentSnapshotImpl(text, modState)
  }

  override fun applyOp(op: DocumentOp): DocumentSnapshot {
    val newText = when (op) {
      is DocumentOp.Text -> text.applyOp(op)
      is DocumentOp.ModStamp, is DocumentOp.CleanLines -> text
    }
    val newModState = modState.applyOp(text, newText, op)
    if (newText === text && newModState === modState) {
      return this
    }
    return DocumentSnapshotImpl(newText, newModState)
  }

  override fun toString(): String {
    val id = Integer.toHexString(System.identityHashCode(this))
    return "DocumentSnapshot@$id{text=$text, modState=$modState}"
  }
}
