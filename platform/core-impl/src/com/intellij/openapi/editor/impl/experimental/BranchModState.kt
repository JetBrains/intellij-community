// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.experimental

import com.intellij.openapi.editor.ex.DocumentModState
import com.intellij.openapi.editor.ex.DocumentOp
import com.intellij.openapi.editor.ex.DocumentText
import com.intellij.openapi.editor.ex.applyOp

/**
 * The mod state of a branch: [Ready], or [Deferred] until the first read.
 *
 * Only a fast-forward makes a [Deferred] state, because its ops wait until a caller asks for them. It
 * starts from a resolved mod state, and every other transition resolves first. So folds never nest.
 */
internal sealed interface BranchModState {

  fun resolved(): DocumentModState

  private class Ready(
    private val modState: DocumentModState,
  ) : BranchModState {
    override fun resolved(): DocumentModState {
      return modState
    }

    override fun toString(): String {
      return modState.toString()
    }
  }

  /**
   * [start] with [ops] folded in, starting from [startText]. The fold runs once, on the first read.
   */
  private class Deferred(
    startText: DocumentText,
    start: DocumentModState,
    ops: () -> List<DocumentOp.Text>,
  ) : BranchModState {
    private val folded by lazy(LazyThreadSafetyMode.PUBLICATION) {
      foldModState(startText, start, ops())
    }

    override fun resolved(): DocumentModState {
      return folded
    }

    /**
     * Shows no mod state, because a print must not run the fold.
     */
    override fun toString(): String {
      return "deferred"
    }
  }

  companion object {
    fun ready(modState: DocumentModState): BranchModState {
      return Ready(modState)
    }

    fun deferred(
      startText: DocumentText,
      start: DocumentModState,
      ops: () -> List<DocumentOp.Text>,
    ): BranchModState {
      return Deferred(startText, start, ops)
    }

    /**
     * [modState] with [ops] applied one after another, starting from [text].
     */
    fun foldModState(
      text: DocumentText,
      modState: DocumentModState,
      ops: List<DocumentOp.Text>,
    ): DocumentModState {
      var before = text
      var folded = modState
      for (op in ops) {
        val after = before.applyOp(op)
        folded = folded.applyOp(before, after, op)
        before = after
      }
      return folded
    }
  }
}
