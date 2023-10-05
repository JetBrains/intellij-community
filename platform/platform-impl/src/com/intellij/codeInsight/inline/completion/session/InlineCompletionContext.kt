// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.session

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
class InlineCompletionContext internal constructor(val editor: Editor) : Disposable {

  /**
   * @see dispose
   */
  var isDisposed = false
    @RequiresEdt
    get
    private set

  private val myState = InlineState().also {
    Disposer.register(this, it)
  }

  val state: InlineState
    @RequiresEdt
    get() = assureNotDisposed { myState }

  val isCurrentlyDisplayingInlays: Boolean
    @RequiresEdt
    get() = state.elements.any { !it.isEmpty }

  val startOffset: Int?
    @RequiresEdt
    get() = state.firstElement?.startOffset

  val endOffset: Int?
    @RequiresEdt
    get() = state.lastElement?.endOffset

  val lineToInsert: String
    @RequiresEdt
    get() = state.elements.joinToString("") { it.text }

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

  private inline fun <T> assureNotDisposed(block: () -> T): T {
    check(!isDisposed) { "Context is disposed. Cannot access elements." }
    return block()
  }

  companion object {

    @RequiresEdt
    fun getOrNull(editor: Editor): InlineCompletionContext? = InlineCompletionSession.getOrNull(editor)?.context

    @Deprecated(
      "Resetting completion context is unsafe now. Use direct get/reset/remove~InlineCompletionContext instead",
      ReplaceWith("getInlineCompletionContextOrNull()"), DeprecationLevel.ERROR
    )
    @RequiresEdt
    fun Editor.initOrGetInlineCompletionContext(): InlineCompletionContext {
      return getOrNull(this)!!
    }

    @Deprecated(
      "Use direct InlineCompletionContext.getOrNull instead",
      ReplaceWith("InlineCompletionContext.getOrNull(this)"), DeprecationLevel.ERROR
    )
    @RequiresEdt
    fun Editor.getInlineCompletionContextOrNull(): InlineCompletionContext? = getOrNull(this)
  }
}
