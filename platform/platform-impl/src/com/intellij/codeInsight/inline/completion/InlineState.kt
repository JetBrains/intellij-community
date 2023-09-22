// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.codeInsight.inline.completion.render.InlineCompletionRenderWatcher
import com.intellij.openapi.CompositeDisposable
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.annotations.RequiresEdt
import java.util.*

data class InlineState internal constructor(
  var lastStartOffset: Int = 0,
  var lastModificationStamp: Long = 0,
) {
  private val _elements: MutableList<InlineCompletionElement> = LinkedList()
  val elements: List<InlineCompletionElement>
    get() = _elements

  val firstElement
    get() = elements.firstOrNull()

  val lastElement
    get() = elements.lastOrNull()

  @RequiresEdt
  internal fun addElement(element: InlineCompletionElement) {
    InlineCompletionRenderWatcher.getInstance().register(element)
    _elements.add(element)
  }

  @RequiresEdt
  internal fun clear() {
    val disposer = CompositeDisposable()
    val renderWatcher = InlineCompletionRenderWatcher.getInstance()
    _elements.forEach {
      disposer.add(it)
      renderWatcher.dispose(it)
    }
    _elements.clear()
    Disposer.dispose(disposer)
  }
}
