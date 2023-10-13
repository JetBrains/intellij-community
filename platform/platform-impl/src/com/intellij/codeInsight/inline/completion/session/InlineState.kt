// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.session

import com.intellij.codeInsight.inline.completion.elements.InlineCompletionElement
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.annotations.RequiresEdt
import java.util.*

data class InlineState internal constructor(
  var lastStartOffset: Int = 0,
  var lastModificationStamp: Long = 0,
) : Disposable {

  private val elementsDisposable = Disposer.newDisposable(this)

  private val _elements: MutableList<InlineCompletionElement.Presentable> = LinkedList()
  val elements: List<InlineCompletionElement.Presentable>
    get() = _elements

  val firstElement
    get() = elements.firstOrNull()

  val lastElement
    get() = elements.lastOrNull()

  @RequiresEdt
  internal fun addElement(element: InlineCompletionElement.Presentable) {
    Disposer.register(elementsDisposable, element)
    _elements.add(element)
  }

  @RequiresEdt
  internal fun clear() {
    _elements.clear()
    Disposer.disposeChildren(elementsDisposable) { true }
  }

  @RequiresEdt
  override fun dispose() {
    clear()
  }
}
