// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.openapi.editor.Editor
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
class InlineCompletionContext internal constructor(val editor: Editor) {

  /**
   * @see invalidate
   */
  var isInvalidated = false
    @RequiresEdt
    get
    private set

  private val myState = InlineState()

  val state: InlineState
    @RequiresEdt
    get() = assureNotInvalidated { myState }

  val isCurrentlyDisplayingInlays: Boolean
    @RequiresEdt
    get() = state.elements.any { !it.isEmpty }

  val startOffset: Int?
    @RequiresEdt
    get() = state.firstElement?.offset

  val lastOffset: Int?
    @RequiresEdt
    get() = state.lastElement?.offset

  val lineToInsert: String
    @RequiresEdt
    get() = state.elements.joinToString("") { it.text }

  @RequiresEdt
  fun clear() {
    assureNotInvalidated { state.clear() }
  }

  /**
   * Indicates that this context cannot be used anymore, meaning that this context cannot be used to access any elements.
   * Any such operation with context after invalidation results into throwing an exception.
   *
   * * The only operation you can safely use is [isInvalidated]. Always check it before accessing any elements.
   * * If this context was already invalidated, this method does nothing.
   * * Invalidation of a context guarantees that all elements were cleared.
   */
  @RequiresEdt
  internal fun invalidate() {
    isInvalidated = true
  }

  private inline fun <T> assureNotInvalidated(block: () -> T): T {
    check(!isInvalidated) { "Context is invalidated. Cannot access elements." }
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
