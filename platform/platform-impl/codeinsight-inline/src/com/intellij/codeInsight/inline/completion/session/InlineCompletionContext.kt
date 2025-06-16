// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.session

import com.intellij.lang.Language
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.util.concurrency.annotations.RequiresEdt

class InlineCompletionContext internal constructor(
  val editor: Editor,
  val language: Language,
  internal var expectedStartOffset: Int // TODO take out to another place. It should not be here. (Tech debt)
) : UserDataHolderBase(), Disposable {
  private val myState = InlineCompletionState().also {
    Disposer.register(this, it)
  }

  /**
   * @see dispose
   */
  @Volatile
  var isDisposed: Boolean = false
    @RequiresEdt
    get
    private set

  val state: InlineCompletionState
    @RequiresEdt
    get() = assureNotDisposed { myState }

  @RequiresEdt
  fun isCurrentlyDisplaying(): Boolean = state.elements.any { it.isVisible() }

  @RequiresEdt
  fun startOffset(): Int? = state.firstElement()?.startOffset()

  @RequiresEdt
  fun endOffset(): Int? = state.lastElement()?.endOffset()

  @RequiresEdt
  fun textToInsert(): String = state.elements.joinToString("") { it.element.text }

  @RequiresEdt
  fun clear() {
    assureNotDisposed { state.clear() }
  }

  /**
   * Indicates that this context cannot be used anymore, meaning that this context cannot be used to access any elements.
   * Any such operation with context after disposing results into throwing an exception.
   * * The only operation you can safely use is [isDisposed]. Always check it before accessing any elements.
   * * Disposing of a context guarantees that all elements were cleared.
   */
  @RequiresEdt
  override fun dispose() {
    clear()
    isDisposed = true
  }

  override fun toString(): String {
    return if (!isDisposed) {
      "InlineCompletionContext(disposed=false, textToInsert=${textToInsert()})"
    }
    else {
      "InlineCompletionContext(disposed=true)"
    }
  }

  private inline fun <T> assureNotDisposed(block: () -> T): T {
    check(!isDisposed) { "Context is disposed. Cannot access elements." }
    return block()
  }

  companion object {

    @RequiresEdt
    fun getOrNull(editor: Editor): InlineCompletionContext? = InlineCompletionSession.getOrNull(editor)?.context
  }
}
