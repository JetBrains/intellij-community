// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.openapi.CompositeDisposable
import com.intellij.openapi.util.Disposer
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

  internal fun addElement(element: InlineCompletionElement) {
    _elements.add(element)
  }

  fun clear() {
    val disposer = CompositeDisposable()
    _elements.forEach {
      disposer.add(it)
    }
    _elements.clear()
    Disposer.dispose(disposer)
  }
}
